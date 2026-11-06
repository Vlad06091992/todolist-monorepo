import { Module } from '@nestjs/common';
import { AppController } from './app.controller';
import { SummarizationService } from './summarization.service';
import GigaChat from 'gigachat';
import { Agent } from 'node:https';
import { ConfigModule } from '@nestjs/config';
import { KafkaModule } from './kafka.module';
import { AppService } from './app.service';

@Module({
  imports: [ConfigModule.forRoot(), KafkaModule],
  controllers: [AppController],
  providers: [AppService, SummarizationService, GigaChat, Agent],
})
export class AppModule {}
