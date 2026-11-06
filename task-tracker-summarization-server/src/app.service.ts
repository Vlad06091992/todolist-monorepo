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
          'KAFKA_TOPIC_TASK_TRACKER_SCHEDULER_REPORTS_REQUESTS',
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
        const messageFromTasksTrackerScheduler =
          message?.value?.toString() || '';

        this.logger.log({
          value: messageFromTasksTrackerScheduler,
          topic: topic.toString(),
          partition: partition.toString(),
        });

        const dataForReport = this.summarizationService.mapToReport(
          JSON.parse(messageFromTasksTrackerScheduler || ''),
        );

        const chatMessage = dataForReport.payload;
        const result =
          await this.summarizationService.sendMessageWithPrompt(chatMessage);

        this.logger.log('GIGACHAT RESPONSE:', result);

        this.produce({
          // topic: 'SCHEDULER_SENDING_TASKS',
          topic:  this.configService.getOrThrow<string>(
            'KAFKA_TOPIC_TASK_TRACKER_SCHEDULER_REPORTS_REQUESTS',
          ),
          messages: [
            {
              value: JSON.stringify(result),
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
    this.producer.send(record);
  }

  async onApplicationShutdown() {
    await this.consumer.disconnect();
  }
}
