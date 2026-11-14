import {
  Inject,
  Injectable,
  Logger,
  OnApplicationShutdown,
  OnModuleInit,
} from '@nestjs/common';
import {
  Consumer,
  ConsumerRunConfig,
  ConsumerSubscribeTopics,
  Kafka,
  Producer,
  ProducerRecord,
} from 'kafkajs';
import { KAFKA_TOKEN } from './kafka.token';
import { SummarizationService } from './summarization.service';
import { ConfigService } from '@nestjs/config';

@Injectable()
export class AppService implements OnModuleInit, OnApplicationShutdown {
  private readonly consumer: Consumer;
  private readonly producer: Producer;
  private count: number = 0;
  private readonly topics: { topics: string[] };
  private readonly logger = new Logger(AppService.name);

  constructor(
    @Inject(KAFKA_TOKEN) private readonly kafka: Kafka,
    private readonly summarizationService: SummarizationService,
    private configService: ConfigService,
  ) {
    this.topics = {
      topics: [
        this.configService.getOrThrow<string>(
          'APP_KAFKA_TOPIC_REPORTS_SUMMARIES_REQUESTED',
        ),
      ],
    };
    this.consumer = this.kafka.consumer({ groupId: 'nestjs-kafka' });
    this.producer = this.kafka.producer();
  }

  async onModuleInit() {
    await this.producer.connect();
    await this.consumer.connect();

    const config: ConsumerRunConfig = {
      eachMessage: async ({ topic, partition, message }): Promise<void> => {
        // eachMessage: async ({ topic, partition, message }): Promise<void> => {
        debugger;

        this.count++;

        const raw = message?.value?.toString() || '';
        // eslint-disable-next-line @typescript-eslint/no-unsafe-assignment
        const parse = JSON.parse(raw);
        const email = raw ? parse[0].email : undefined;
        debugger;
        const messageFromTasksTrackerScheduler =
          message?.value?.toString() || '';

        const headers = message?.headers;
        debugger;
        if (headers) {
          console.log('ПОЛУЧЕНО СООБЩЕНИЕ' + new Date().toString());
          console.log('CORRELATION_ID', headers['CORRELATION_ID']?.toString());
          console.log('COUNT_NUMBER', headers['COUNT_NUMBER']?.toString());
          debugger;
        }

        // this.logger.log({
        //   value: messageFromTasksTrackerScheduler,
        //   topic: topic.toString(),
        //   partition: partition.toString(),
        // });

        const dataForReport = this.summarizationService.mapToReport(
          JSON.parse(messageFromTasksTrackerScheduler || ''),
        );

        const chatMessage = dataForReport.payload;
        const result =
          await this.summarizationService.sendMessageWithPrompt(chatMessage);

        // const result = [
        //   {
        //     login: 'vlad',
        //     message: `(MOCK-${this.count}) Привет, Влад! ${email} За последние сутки ты успешно завершил две задачи: почесался и попил квас. Продолжай в том же духе, квасной герой!`,
        //   },
        // ];

        debugger

        // this.logger.log('GIGACHAT RESPONSE:', result);
       await this.produce({
          // topic: 'SCHEDULER_SENDING_TASKS',
          topic: this.configService.getOrThrow<string>(
            'APP_KAFKA_TOPIC_REPORTS_SUMMARIES_COMPLETED',
          ),
          messages: [
            {
              value: JSON.stringify(result),
              headers,
            },
          ],
        });
      },
    };
    await this.consume(config);
  }

  async consume(config: ConsumerRunConfig) {
    await this.consumer.subscribe(this.topics);
    await this.consumer.run(config);
  }



  async produce(record: ProducerRecord) {
    // this.logger.log('send message', record);
    this.producer.send(record);
    // console.log("CALL time" + new Date().toString());

    // setTimeout(
    //   () => {
    //     // console.log("SEND time" + new Date().toString());
    //     this.producer.send(record);
    //   },
    //   this.delay(5, 15),
    // );
  }

  async onApplicationShutdown() {
    await this.consumer.disconnect();
  }

  private delay(min: number, max: number): number {
    const seconds = Math.floor(Math.random() * (max - min + 1)) + min;
    const ms = seconds * 1000;
    console.log(`SEND через ${ms} мс`);
    return ms;
  }
}
