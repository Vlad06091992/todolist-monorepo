import {
  Inject,
  Injectable,
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

@Injectable()
export class ConsumerService implements OnModuleInit, OnApplicationShutdown {
  // Connect to Kafka Server

  private readonly consumer: Consumer;
  private readonly producer: Producer;

  // onModuleInit() {
  //   this.kafka =
  //   this.consumer =
  //
  // }

  constructor(@Inject(KAFKA_TOKEN) private readonly kafka: Kafka) {
    this.consumer = this.kafka.consumer({ groupId: 'nestjs-kafka' });
    this.producer = this.kafka.producer();
  }
  async onModuleInit() {
    await this.consume(
      { topics: ['test'] },
      {
        eachMessage: ({ topic, partition, message }): any => {
          console.log({
            value: message?.value?.toString(),
            topic: topic.toString(),
            partition: partition.toString(),
          });

          this.produce({
            topic: 'SCHEDULER_SENDING_TASKS',
            messages: [
              {
                value: 'Hello from nestJS',
              },
            ],
          });
        },
      },
    );

    await this.producer.connect();
  }

  // private readonly consumers: kafkajs.Consumer[] = [];

  async consume(topics: ConsumerSubscribeTopics, config: ConsumerRunConfig) {
    // We need to spcifiy the groupID while initializing the Kafka Consumer

    // Connecting Consumer
    await this.consumer.connect();

    //Passing Topics to consumer
    await this.consumer.subscribe(topics);

    //Setting  the Consumer Config
    await this.consumer.run(config);

    // //Gathering all the Consumers
    // this.consumers.push(consumer);
  }

  // async
  produce(record: ProducerRecord) {
    //Send Records to Kafka to producer
    this.producer.send(record);
  }

  async onApplicationShutdown() {
    // Disconnect all the consumer on Apllication shutdown
    // for (const consumer of this.consumers) {
    await this.consumer.disconnect();
    // }
  }
}
