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
import { KAFKA_CLIENT } from './kafka.token';
import { SummarizationService } from './summarization.service';
import { ConfigService } from '@nestjs/config';

@Injectable()
export class SummarizationConsumerService
  implements OnModuleInit, OnApplicationShutdown
{
  private readonly consumer: Consumer;
  private readonly producer: Producer;
  private readonly subscription: ConsumerSubscribeTopics;
  private readonly logger = new Logger(SummarizationConsumerService.name);
  private readonly consumerRunConfig: ConsumerRunConfig = {
    eachMessage: async ({ topic, partition, message }): Promise<void> => {
      const rawMessage = message?.value?.toString() || '';
      const headers = message?.headers;

      const promptUsers = this.summarizationService.buildPromptPayload(
        // eslint-disable-next-line @typescript-eslint/no-unsafe-argument
        JSON.parse(rawMessage || ''),
      );

      const result =
        await this.summarizationService.generateReport(promptUsers);
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
    private readonly configService: ConfigService,
  ) {
    this.subscription = {
      topics: [
        this.configService.getOrThrow<string>(
          'APP_KAFKA_TOPIC_REPORTS_SUMMARIES_REQUESTED',
        ),
      ],
    };
    this.consumer = this.kafka.consumer({
      groupId: this.configService.get<string>(
        'KAFKA_CONSUMER_GROUP_ID',
        'task-tracker-summarization-server',
      ),
    });
    this.producer = this.kafka.producer();
  }

  async onModuleInit() {
    await this.producer.connect();
    await this.consumer.connect();
    await this.startConsuming(this.consumerRunConfig);
  }

  async startConsuming(runConfig: ConsumerRunConfig) {
    await this.consumer.subscribe(this.subscription);
    await this.consumer.run(runConfig);
  }

  async produce(record: ProducerRecord) {
    await this.producer.send(record);
  }

  async onApplicationShutdown() {
    await this.consumer.disconnect();
  }
}
