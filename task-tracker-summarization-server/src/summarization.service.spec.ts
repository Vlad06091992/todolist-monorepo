import GigaChat from 'gigachat';
import { ConfigService } from '@nestjs/config';
import { PromptUser, ReportDTO, UserSummary } from './contracts';
import { SummarizationService } from './summarization.service';

interface GigaChatResponse {
  choices: Array<{ message: { content?: string } }>;
}

const userSummaries: UserSummary[] = [
  {
    id: 'user-1',
    login: 'Andriusha',
    email: 'andriusha@example.com',
    tasks: ['попить молока', 'потренироваться'],
  },
  {
    id: 'user-2',
    login: 'Anna',
    email: 'anna@example.com',
    tasks: [],
  },
];

const expectedPromptUsers: PromptUser[] = [
  {
    login: 'Andriusha',
    completedLast24h: ['попить молока', 'потренироваться'],
    activeTasks: 2,
  },
  { login: 'Anna', completedLast24h: [], activeTasks: 0 },
];

const expectedReports: ReportDTO[] = [
  { login: 'Andriusha', message: 'Отличная работа!' },
];

describe('SummarizationService', () => {
  let service: SummarizationService;
  let chat: ReturnType<typeof createChatMock>;
  let chatRequest: unknown;

  function createChatMock() {
    return jest.fn(async (request: unknown): Promise<GigaChatResponse> => {
      chatRequest = request;
      throw new Error(
        'Ответ GigaChat должен быть замокан: chat.mockResolvedValueOnce(...)',
      );
    });
  }

  beforeEach(() => {
    chatRequest = undefined;
    chat = createChatMock();

    const configService = {
      getOrThrow: jest.fn().mockReturnValue('test-credentials'),
    } as unknown as ConfigService;

    service = new SummarizationService(configService);
    // Подменяем реальный GigaChat-клиент, чтобы тесты не ходили в сеть.
    (service as unknown as { gigaChatClient: GigaChat }).gigaChatClient = {
      chat,
    } as unknown as GigaChat;
  });

  describe('buildPromptPayload', () => {
    it('маппит UserSummary в PromptUser: считает activeTasks и переносит задачи', () => {
      const result = service.buildPromptPayload(userSummaries);

      expect(result).toEqual(expectedPromptUsers);
    });

    it('не включает id и email в payload для промпта', () => {
      const result = service.buildPromptPayload(userSummaries);

      expect(result[0]).not.toHaveProperty('id');
      expect(result[0]).not.toHaveProperty('email');
    });

    it('возвращает пустой массив, если пользователей нет', () => {
      expect(service.buildPromptPayload([])).toEqual([]);
    });
  });

  describe('generateReport', () => {
    it('парсит голый JSON-массив из ответа GigaChat', async () => {
      chat.mockResolvedValueOnce({
        choices: [
          {
            message: {
              content: '[{"login":"Andriusha","message":"Отличная работа!"}]',
            },
          },
        ],
      });

      await expect(
        service.generateReport(expectedPromptUsers),
      ).resolves.toEqual(expectedReports);
    });

    it('вырезает JSON-массив из ответа с лишним текстом и markdown-обёрткой', async () => {
      chat.mockResolvedValueOnce({
        choices: [
          {
            message: {
              content:
                '```json\nВот отчёт за сутки:\n' +
                '[{"login":"Andriusha","message":"Отличная работа!"}]\n' +
                'Хорошего дня!\n```',
            },
          },
        ],
      });

      await expect(
        service.generateReport(expectedPromptUsers),
      ).resolves.toEqual(expectedReports);
    });

    it('отправляет в GigaChat промпт с payload и шаблоном в роли user', async () => {
      // Важно: mockResolvedValueOnce подменяет реализацию ЦЕЛИКОМ — дефолтная
      // не выполняется и chatRequest остаётся undefined. Поэтому once-реализация
      // сама записывает полученный запрос.
      chat.mockImplementationOnce(
        async (request: unknown): Promise<GigaChatResponse> => {
          chatRequest = request;
          return { choices: [{ message: { content: '[]' } }] };
        },
      );

      await service.generateReport(expectedPromptUsers);

      expect(chatRequest).toEqual({
        messages: [
          {
            role: 'user',
            content: expect.stringContaining(
              JSON.stringify(expectedPromptUsers),
            ),
          },
        ],
      });
    });

    it('падает с SyntaxError, если в ответе модели нет JSON-массива', async () => {
      chat.mockResolvedValueOnce({
        choices: [{ message: { content: 'Отчёта не будет' } }],
      });

      // Текущее поведение: extractJsonArray вернёт '' и JSON.parse('') кинет
      // SyntaxError. Фиксируем, чтобы заметить изменение при рефакторинге.
      await expect(
        service.generateReport(expectedPromptUsers),
      ).rejects.toThrow(SyntaxError);
    });

    it('падает с SyntaxError, если у ответа модели нет текста', async () => {
      chat.mockResolvedValueOnce({
        choices: [{ message: {} }],
      });

      await expect(
        service.generateReport(expectedPromptUsers),
      ).rejects.toThrow(SyntaxError);
    });
  });
});
