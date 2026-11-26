import { Test, TestingModule } from '@nestjs/testing';
import { ConfigService } from '@nestjs/config';
import {
  ConsumerRunConfig,
  EachMessagePayload,
  Kafka,
  ProducerRecord,
} from 'kafkajs';
import { PromptUser, ReportDTO, UserSummary } from './contracts';
import { SummarizationConsumerService } from './summarization.consumer';
import { KAFKA_CLIENT } from './kafka.token';
import { SummarizationService } from './summarization.service';

const TOPICS = {
  requested: 'reports.summarization.requested',
  completed: 'reports.summarization.completed',
};

const configValues: Record<string, string> = {
  APP_KAFKA_TOPIC_REPORTS_SUMMARIES_REQUESTED: TOPICS.requested,
  APP_KAFKA_TOPIC_REPORTS_SUMMARIES_COMPLETED: TOPICS.completed,
  KAFKA_CONSUMER_GROUP_ID: 'test-group',
};

const userSummaries: UserSummary[] = [
  {
    id: 'user-1',
    login: 'Andriusha',
    email: 'andriusha@example.com',
    tasks: ['попить молока', 'потренироваться'],
  },
];

const expectedPromptUsers: PromptUser[] = [
  {
    login: 'Andriusha',
    completedLast24h: ['попить молока', 'потренироваться'],
    activeTasks: 2,
  },
];

const reports: ReportDTO[] = [
  { login: 'Andriusha', message: 'Привет, Андрюша! Отличная работа!' },
];

describe('SummarizationConsumerService', () => {
  let service: SummarizationConsumerService;

  // Захваченные вызовы — вместо индексации нетипизированных mock.calls.
  let consumerOptions: unknown;
  let subscribedTopics: unknown;
  let runConfig: ConsumerRunConfig | undefined;
  let producedRecord: ProducerRecord | undefined;

  const consumerMock = {
    connect: jest.fn(async (): Promise<void> => undefined),
    subscribe: jest.fn(async (subscription: unknown): Promise<void> => {
      subscribedTopics = subscription;
    }),
    run: jest.fn(async (config: ConsumerRunConfig): Promise<void> => {
      runConfig = config;
    }),
    disconnect: jest.fn(async (): Promise<void> => undefined),
  };

  const producerMock = {
    connect: jest.fn(async (): Promise<void> => undefined),
    send: jest.fn(async (record: ProducerRecord): Promise<void> => {
      producedRecord = record;
    }),
  };

  const kafkaMock = {
    consumer: jest.fn((options: unknown) => {
      consumerOptions = options;
      return consumerMock;
    }),
    producer: jest.fn(() => producerMock),
  };

  // Дефолты имитируют реальное поведение сервиса, чтобы каждый тест
  // переопределял только то, что проверяет.
  const summarizationServiceMock = {
    buildPromptPayload: jest.fn(
      (users: UserSummary[]): PromptUser[] =>
        users.map((user) => ({
          login: user.login,
          completedLast24h: user.tasks,
          activeTasks: user.tasks.length,
        })),
    ),
    generateReport: jest.fn(
      async (users: PromptUser[]): Promise<ReportDTO[]> =>
        users.map((user) => ({ login: user.login, message: '' })),
    ),
  };

  const configServiceMock = {
    getOrThrow: jest.fn((key: string): string => {
      const value = configValues[key];
      if (!value) throw new Error(`Не задан конфиг: ${key}`);
      return value;
    }),
    get: jest.fn(
      (key: string, defaultValue?: string): string | undefined =>
        configValues[key] ?? defaultValue,
    ),
  };

  function getEachMessage(): (payload: EachMessagePayload) => Promise<void> {
    const eachMessage = runConfig?.eachMessage;
    if (!eachMessage) {
      throw new Error('eachMessage не был зарегистрирован через consumer.run()');
    }
    return eachMessage;
  }

  beforeEach(async () => {
    jest.clearAllMocks();
    consumerOptions = undefined;
    subscribedTopics = undefined;
    runConfig = undefined;
    producedRecord = undefined;

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        SummarizationConsumerService,
        { provide: KAFKA_CLIENT, useValue: kafkaMock },
        {
          provide: SummarizationService,
          useValue: summarizationServiceMock,
        },
        { provide: ConfigService, useValue: configServiceMock },
      ],
    }).compile();

    service = module.get<SummarizationConsumerService>(
      SummarizationConsumerService,
    );
  });

  it('создаёт consumer с groupId из конфигурации', () => {
    expect(consumerOptions).toEqual({ groupId: 'test-group' });
  });

  it('onModuleInit подключает producer и consumer, подписывается на топик и запускает обработку', async () => {
    await service.onModuleInit();

    expect(producerMock.connect).toHaveBeenCalledTimes(1);
    expect(consumerMock.connect).toHaveBeenCalledTimes(1);
    expect(subscribedTopics).toEqual({ topics: [TOPICS.requested] });
    expect(consumerMock.run).toHaveBeenCalledTimes(1);
  });

  it('eachMessage разбирает входящее сообщение, суммаризирует и публикует отчёт в completed-топик', async () => {
    summarizationServiceMock.generateReport.mockResolvedValueOnce(reports);

    await service.onModuleInit();
    const eachMessage = getEachMessage();

    await eachMessage({
      topic: TOPICS.requested,
      partition: 0,
      message: {
        value: Buffer.from(JSON.stringify(userSummaries)),
        headers: { CORRELATION_ID: Buffer.from('corr-123') },
      },
      heartbeat: async () => undefined,
      pause: () => () => undefined,
    });

    expect(summarizationServiceMock.buildPromptPayload).toHaveBeenCalledWith(
      userSummaries,
    );
    expect(summarizationServiceMock.generateReport).toHaveBeenCalledWith(
      expectedPromptUsers,
    );
    expect(producedRecord).toEqual({
      topic: TOPICS.completed,
      messages: [
        {
          value: JSON.stringify(reports),
          headers: { CORRELATION_ID: Buffer.from('corr-123') },
        },
      ],
    });
  });

  it('eachMessage пробрасывает исходные Kafka-заголовки (CORRELATION_ID) в исходящее сообщение', async () => {
    await service.onModuleInit();
    const eachMessage = getEachMessage();

    const headers = { CORRELATION_ID: Buffer.from('rpc-42') };
    await eachMessage({
      topic: TOPICS.requested,
      partition: 0,
      message: { value: Buffer.from('[]'), headers },
      heartbeat: async () => undefined,
      pause: () => () => undefined,
    });

    expect(producedRecord?.messages[0].headers).toEqual(headers);
  });

  it('eachMessage падает с SyntaxError на пустом сообщении', async () => {
    await service.onModuleInit();
    const eachMessage = getEachMessage();

    // Текущее поведение: JSON.parse('') кидает SyntaxError, обработчик
    // не обёрнут в try/catch. Фиксируем, чтобы заметить при рефакторинге.
    await expect(
      eachMessage({
        topic: TOPICS.requested,
        partition: 0,
        message: { value: Buffer.from(''), headers: {} },
        heartbeat: async () => undefined,
        pause: () => () => undefined,
      }),
    ).rejects.toThrow(SyntaxError);
  });

  it('onApplicationShutdown отключает consumer', async () => {
    await service.onApplicationShutdown();

    expect(consumerMock.disconnect).toHaveBeenCalledTimes(1);
  });
});
