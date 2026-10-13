import GigaChat from 'gigachat';
import { Agent } from 'node:https';
import { Injectable } from '@nestjs/common';
import { ReportDTO, Summarization, UserDTO } from './interfaces';
import { ConfigService } from '@nestjs/config';

@Injectable()
export class GigaChatSummarization implements Summarization {
  httpsAgent: Agent;
  client: GigaChat;
  private promptTemplate = `Для этих пользователей сформируй отчет для отправки по емайл о
     сделанных задачах за сутки, отчет должен быть сгенерирован
      в формате json(массив с объектами) в ответ на payload, у объекта есть 
      login, и message - сам отчет в виде одной строки без специсимволов, в котором есть приветствие, сам отчет, и немножко креатива в конце. 
      В ответе от тебя ожидаю только голый(без каких либо спецсимволов, 
      обозначений, и прочего, например ) json для корректного превращения в DTO,`;

  constructor(private configService: ConfigService) {
    const credentials =
      this.configService.getOrThrow<string>('CHAT_CREDENTIALS');
    this.httpsAgent = new Agent({
      rejectUnauthorized: false, // Отключает проверку корневого сертификата
      // Читайте ниже как можно включить проверку сертификата НУЦ Минцифры
    });

    this.client = new GigaChat({
      timeout: 600_000,
      model: 'GigaChat-2-Pro', // Это имя будет работать на новом хосте
      credentials,
      httpsAgent: this.httpsAgent,
      baseUrl: 'https://api.giga.chat/v1', // Явно указываем новый адрес
    });
  }

  public async sendMessageWithPrompt(
    usersDto: UserDTO[],
  ): Promise<ReportDTO[]> {
    const prompt = this.generatePrompt(usersDto);
    const result = await this.client.chat({
      messages: [{ role: 'user', content: prompt }],
    });
    const message = this.extractArray(result?.choices[0].message.content);
    // eslint-disable-next-line @typescript-eslint/no-unsafe-return
    return JSON.parse(message);
  }

  private generatePrompt(usersDto: UserDTO[]): string {
    return this.promptTemplate + `payload:  ${JSON.stringify(usersDto)}`;
  }

  private extractArray(str: string | undefined): string {
    if (!str) return '';
    const start = str.indexOf('[');
    const end = str.lastIndexOf(']');
    if (start === -1 || end === -1) return '';
    return str.slice(start, end + 1);
  }
}
