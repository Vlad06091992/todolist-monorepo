import { Body, Controller, Post } from '@nestjs/common';
import { SummarizationService } from './summarization.service';
import { UserDTO } from './interfaces';

import {
  ArrayMaxSize,
  ArrayMinSize,
  ArrayNotEmpty,
  IsArray,
  IsNotEmpty,
  ValidateNested,
} from 'class-validator';
import { Type } from 'class-transformer';

export class CreateMessageDto {
  @IsArray()
  @ArrayNotEmpty()
  @ArrayMinSize(1)
  @ArrayMaxSize(10)
  @IsNotEmpty()
  @ValidateNested({ each: true })
  @Type(() => UserDTO)
  payload: UserDTO[];
}

@Controller('/dev')
export class AppController {
  constructor(private readonly chat: SummarizationService) {}

  @Post('/send')
  async sendMessage(@Body() createDto: CreateMessageDto) {
    try {
      return await this.chat.sendMessageWithPrompt(createDto.payload);
    } catch (e) {
      console.error(e);
    }
  }
}
