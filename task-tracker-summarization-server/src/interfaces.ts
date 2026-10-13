export interface UserDTO {
  login: string;
  completedLast24h: string[];
  activeTasks: number;
}

export interface ReportDTO {
  login: string;
  message: string;
}

export interface Summarization {
  sendMessageWithPrompt(usersDto: UserDTO[]): Promise<ReportDTO[]>;
}
