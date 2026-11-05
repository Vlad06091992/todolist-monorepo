export class UserDTO {
  login: string;
  completedLast24h: string[];
  activeTasks: number;
}

export interface ReportPayload {
  payload: UserDTO[];
}

export interface ReportDTO {
  login: string;
  message: string;
}

export interface Summarization {
  sendMessageWithPrompt(usersDto: UserDTO[]): Promise<ReportDTO[]>;
}

export interface UserSummary {
  id: string;
  login: string;
  tasks: string[];
}