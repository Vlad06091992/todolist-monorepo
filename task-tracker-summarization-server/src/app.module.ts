import { Module } from '@nestjs/common';
import { AppController } from './app.controller';
import { AppService } from './app.service';
import { Chat } from './giga';
import GigaChat from 'gigachat';
import { Agent } from 'node:https';

@Module({
  imports: [],
  controllers: [AppController],
  providers: [AppService, Chat, GigaChat, Agent],
})
export class AppModule {}
