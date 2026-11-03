import { Module } from '@nestjs/common';
import { AppController } from './app.controller';
import { AppService } from './app.service';
import { GigaChatSummarization } from './giga-chat';
import GigaChat from 'gigachat';
import { Agent } from 'node:https';
import { ConfigModule } from '@nestjs/config';
import { ConsumerService } from './kafka-consumer';
import { Kafka } from 'kafkajs';
import { KAFKA_TOKEN } from './kafka.token';

@Module({
  imports: [ConfigModule.forRoot()],
  controllers: [AppController],
  providers: [
    AppService,
    GigaChatSummarization,
    GigaChat,
    Agent,
    ConsumerService,
    {
      provide: KAFKA_TOKEN,
      useFactory: () => {
        return new Kafka({ clientId: 'my-app', brokers: ['localhost:9092'] });
      },
    },
  ],
})
export class AppModule {}
