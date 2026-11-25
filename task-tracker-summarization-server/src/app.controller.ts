import { Body, Controller, Logger, Post } from '@nestjs/common';
import { SummarizationService } from './summarization.service';
import { PromptUser } from './contracts';

import {
  ArrayMaxSize,
  ArrayMinSize,
  ArrayNotEmpty,
  IsArray,
  IsNotEmpty,
  ValidateNested,
} from 'class-validator';
import { Type } from 'class-transformer';

export class DevSummarizationRequest {
  @IsArray()
  @ArrayNotEmpty()
  @ArrayMinSize(1)
  @ArrayMaxSize(10)
  @IsNotEmpty()
  @ValidateNested({ each: true })
  @Type(() => PromptUser)
  payload: PromptUser[];
}

@Controller('/dev')
export class AppController {
  private readonly logger = new Logger(AppController.name);
  constructor(private readonly summarizationService: SummarizationService) {}

  @Post('/send')
  async generateReport(@Body() createDto: DevSummarizationRequest) {
    try {
      return await this.summarizationService.generateReport(createDto.payload);
    } catch (e) {
      this.logger.error('Report generation failed', e);
    }
  }
}
