import { Module } from '@nestjs/common';
import { AppController } from './app.controller';
import { AppService } from './app.service';
import { GigaChatSummarization } from './giga-chat';
import GigaChat from 'gigachat';
import { Agent } from 'node:https';
import { ConfigModule } from '@nestjs/config';

@Module({
  imports: [ConfigModule.forRoot()],
  controllers: [AppController],
  providers: [AppService, GigaChatSummarization, GigaChat, Agent],
})
export class AppModule {}
