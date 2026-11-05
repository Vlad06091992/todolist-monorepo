import { Body, Controller, Get, Post } from '@nestjs/common';
import { AppService } from './app.service';
import { GigaChatSummarization } from './giga-chat';
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

// interface CreateMessageDto {
//   payload: UserDTO[];
// }



@Controller('/dev')
export class AppController {
  constructor(
    private readonly appService: AppService,
    private readonly chat: GigaChatSummarization,
  ) {}

  @Post('/send')
  async sendMessage(@Body() createDto: CreateMessageDto) {
    try {
      return await this.chat.sendMessageWithPrompt(createDto.payload);
    } catch (e) {
      console.error(e);
    }
  }
}
