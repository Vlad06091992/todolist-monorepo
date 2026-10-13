import { Controller, Get, Query } from '@nestjs/common';
import { AppService } from './app.service';
import { Chat } from './giga';

@Controller()
export class AppController {
  constructor(
    private readonly appService: AppService,
    private readonly chat: Chat,
  ) {}

  @Get()
  getHello(): string {
    return this.appService.getHello();
  }

  @Get('/send')
  async sendMessage(@Query('message') message: string): Promise<any> {
    try {
      const result = await this.chat.sendMessage(message);
      debugger;
      console.log(result);

      return result;

      return result;
    } catch (e) {
      console.error(e);
      debugger;
    }
  }
}
