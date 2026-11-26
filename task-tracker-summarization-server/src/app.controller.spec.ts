import { Test, TestingModule } from '@nestjs/testing';
import { AppController } from './app.controller';
import { SummarizationService } from './summarization.service';
import { PromptUser } from './contracts';

// Контроллер делегирует генерацию отчёта SummarizationService — его и мокаем,
// чтобы тест не тянул DI-граф AppService (KAFKA_CLIENT, ConfigService, GigaChat).
describe('AppController', () => {
  let appController: AppController;
  let generateReport: jest.Mock;

  beforeEach(async () => {
    generateReport = jest.fn();
    const app: TestingModule = await Test.createTestingModule({
      controllers: [AppController],
      providers: [
        { provide: SummarizationService, useValue: { generateReport } },
      ],
    }).compile();

    appController = app.get<AppController>(AppController);
  });

  describe('POST /dev/send', () => {
    it('возвращает отчёт, сгенерированный SummarizationService', async () => {
      const payload: PromptUser[] = [
        {
          login: 'Andriusha',
          completedLast24h: ['попить молока'],
          activeTasks: 1,
        },
      ];
      const report = [{ login: 'Andriusha', message: 'Отличная работа!' }];
      generateReport.mockResolvedValueOnce(report);

      await expect(appController.generateReport({ payload })).resolves.toEqual(
        report,
      );
      expect(generateReport).toHaveBeenCalledWith(payload);
    });

    it('возвращает undefined при ошибке генерации (ошибка логируется, не пробрасывается)', async () => {
      generateReport.mockRejectedValueOnce(new Error('gigachat down'));

      await expect(
        appController.generateReport({
          payload: [{ login: 'Anna', completedLast24h: [], activeTasks: 0 }],
        }),
      ).resolves.toBeUndefined();
    });
  });
});
