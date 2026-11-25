export class PromptUser {
  login: string;
  completedLast24h: string[];
  activeTasks: number;
}

export interface ReportDTO {
  login: string;
  message: string;
}

export interface Summarizer {
  generateReport(users: PromptUser[]): Promise<ReportDTO[]>;
}

export interface UserSummary {
  id: string;
  login: string;
  email: string;
  tasks: string[];
}
