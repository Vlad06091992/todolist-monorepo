import GigaChat from 'gigachat';
import { Agent } from 'node:https';
import { Injectable } from '@nestjs/common';
import { ChatCompletion } from 'gigachat/interfaces';

@Injectable()
export class Chat {
  httpsAgent: Agent;
  client: GigaChat;

  constructor() {
    this.httpsAgent = new Agent({
      rejectUnauthorized: false, // Отключает проверку корневого сертификата
      // Читайте ниже как можно включить проверку сертификата НУЦ Минцифры
    });

    this.client = new GigaChat({
      timeout: 600_000,
      model: 'GigaChat-2-Pro', // Это имя будет работать на новом хосте
      credentials: '',
      httpsAgent: this.httpsAgent,
      baseUrl: 'https://api.giga.chat/v1', // Явно указываем новый адрес
    });

    // this.client = new GigaChat({
    //   timeout: 600,
    //   model: 'GigaChat',
    //   credentials:
    //     '',
    //   httpsAgent: this.httpsAgent,
    // });
  }

  public async sendMessage(content: string): Promise<string | undefined> {
    const result = await this.client.chat({
      messages: [{ role: 'user', content }],
    });

    const message = result?.choices[0].message.content;

    return message;
  }
}
