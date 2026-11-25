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
  Kafka,
  Producer,
  ProducerRecord,
} from 'kafkajs';
import { SummarizationService } from './summarization.service';
import { ConfigService } from '@nestjs/config';
import { KAFKA_CLIENT } from './kafka.token';

@Injectable()
export class AppService implements OnModuleInit, OnApplicationShutdown {
  private readonly consumer: Consumer;
  private readonly producer: Producer;
  private readonly topics: { topics: string[] };
  private readonly logger = new Logger(AppService.name);
  private readonly config: ConsumerRunConfig = {
    eachMessage: async ({ topic, partition, message }): Promise<void> => {
      const messageFromTasksTrackerScheduler = message?.value?.toString() || '';

      const headers = message?.headers;

      const dataForReport = this.summarizationService.buildPromptPayload(
        // eslint-disable-next-line @typescript-eslint/no-unsafe-argument
        JSON.parse(messageFromTasksTrackerScheduler || ''),
      );

      const result =
        await this.summarizationService.generateReport(dataForReport);
      await this.produce({
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

  constructor(
    @Inject(KAFKA_CLIENT) private readonly kafka: Kafka,
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
    await this.consume(this.config);
  }

  async consume(config: ConsumerRunConfig) {
    await this.consumer.subscribe(this.topics);
    await this.consumer.run(config);
  }

  async produce(record: ProducerRecord) {
    await this.producer.send(record);
  }

  async onApplicationShutdown() {
    await this.consumer.disconnect();
  }
}
