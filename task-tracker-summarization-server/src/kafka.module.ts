import { Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { KAFKA_CLIENT } from './kafka.token';
import { Kafka } from 'kafkajs';

@Module({
  providers: [
    {
      provide: KAFKA_CLIENT,
      inject: [ConfigService],
      useFactory: (configService: ConfigService) =>
        new Kafka({
          clientId: configService.get<string>(
            'KAFKA_CLIENT_ID',
            'task-tracker-summarization-server',
          ),
          brokers: configService
            .get<string>('KAFKA_BROKERS', 'localhost:9092')
            .split(','),
        }),
    },
  ],
  exports: [KAFKA_CLIENT],
})
export class KafkaModule {}
