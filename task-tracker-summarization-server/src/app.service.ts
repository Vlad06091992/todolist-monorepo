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
        debugger;
        const messageFromTasksTrackerScheduler =
          message?.value?.toString() || '';

        const headers = message?.headers;

        this.logger.log({
          value: messageFromTasksTrackerScheduler,
          topic: topic.toString(),
          partition: partition.toString(),
        });

        const dataForReport = this.summarizationService.mapToReport(
          JSON.parse(messageFromTasksTrackerScheduler || ''),
        );

        const chatMessage = dataForReport.payload;
        // const result =
        //   await this.summarizationService.sendMessageWithPrompt(chatMessage);

        const result = [
          {
            login: 'vlad',
            message:
              '(MOCK) Привет, Влад! За последние сутки ты успешно завершил две задачи: почесался и попил квас. Продолжай в том же духе, квасной герой!',
          },
        ];

        this.logger.log('GIGACHAT RESPONSE:', result);
        this.produce({
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

  produce(record: ProducerRecord) {
    debugger;
    this.producer.send(record);
  }

  async onApplicationShutdown() {
    await this.consumer.disconnect();
  }
}
