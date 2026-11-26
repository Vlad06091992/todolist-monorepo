import GigaChat from 'gigachat';
import { Agent } from 'node:https';
import { Injectable } from '@nestjs/common';
import {
  PromptUser,
  ReportDTO,
  Summarizer,
  UserSummary,
} from './contracts';
import { ConfigService } from '@nestjs/config';

@Injectable()
export class SummarizationService implements Summarizer {
  private readonly httpsAgent: Agent;
  private readonly gigaChatClient: GigaChat;
  private promptTemplate = `Для этих пользователей сформируй отчет для отправки по емайл о
     сделанных задачах за сутки, отчет должен быть сгенерирован
      в формате json(массив с объектами) в ответ на payload, у объекта есть 
      login, и message - сам отчет в виде одной строки без специсимволов, в котором есть приветствие, сам отчет, и немножко креатива в конце. 
      В ответе от тебя ожидаю только голый(без каких либо спецсимволов, 
      обозначений, и прочего, например ) json для корректного превращения в DTO,`;

  constructor(private configService: ConfigService) {
    const credentials =
      this.configService.getOrThrow<string>('GIGACHAT_CREDENTIALS');
    this.httpsAgent = new Agent({
      rejectUnauthorized: false, // Отключает проверку корневого сертификата
      // Читайте ниже как можно включить проверку сертификата НУЦ Минцифры
    });

    this.gigaChatClient = new GigaChat({
      timeout: 600_000,
      model: 'GigaChat-2-Pro', // Это имя будет работать на новом хосте
      credentials,
      httpsAgent: this.httpsAgent,
      baseUrl: 'https://api.giga.chat/v1', // Явно указываем новый адрес
    });
  }

  public buildPromptPayload(userSummaries: UserSummary[]): PromptUser[] {
    return userSummaries.map((userSummary) => ({
      activeTasks: userSummary.tasks.length,
      completedLast24h: userSummary.tasks,
      login: userSummary.login,
    }));
  }

  public async generateReport(users: PromptUser[]): Promise<ReportDTO[]> {
    const prompt = this.generatePrompt(users);
    const result = await this.gigaChatClient.chat({
      messages: [{ role: 'user', content: prompt }],
    });
    const message = this.extractJsonArray(result?.choices[0].message.content);
    // eslint-disable-next-line @typescript-eslint/no-unsafe-return
    return JSON.parse(message);
  }

  private generatePrompt(users: PromptUser[]): string {
    return this.promptTemplate + `payload:  ${JSON.stringify(users)}`;
  }

  private extractJsonArray(raw: string | undefined): string {
    if (!raw) return '';
    const start = raw.indexOf('[');
    const end = raw.lastIndexOf(']');
    if (start === -1 || end === -1) return '';
    return raw.slice(start, end + 1);
  }
}
