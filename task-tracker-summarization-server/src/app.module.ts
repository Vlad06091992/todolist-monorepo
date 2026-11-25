import { Module } from '@nestjs/common';
import { AppController } from './app.controller';
import { SummarizationService } from './summarization.service';
import { ConfigModule } from '@nestjs/config';
import { KafkaModule } from './kafka.module';
import { SummarizationConsumerService } from './summarization.consumer';

@Module({
  imports: [ConfigModule.forRoot({ isGlobal: true }), KafkaModule],
  controllers: [AppController],
  providers: [SummarizationConsumerService, SummarizationService],
})
export class AppModule {}
