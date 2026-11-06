import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { KAFKA_TOKEN } from './kafka.token';
import { Kafka } from 'kafkajs';

@Module({
  imports: [ConfigModule.forRoot()],
  providers: [
    {
      provide: KAFKA_TOKEN,
      useFactory: () => {
        return new Kafka({ clientId: 'my-app', brokers: ['localhost:9092'] });
      },
    },
  ],
  exports: [KAFKA_TOKEN],
})
export class KafkaModule {}
