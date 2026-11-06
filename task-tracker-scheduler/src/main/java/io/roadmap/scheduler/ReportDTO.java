package io.roadmap.scheduler;

public record ReportDTO(
        String login,
        String message,
        String email
) {

    public ReportDTO withEmail(String email) {
        return new ReportDTO(this.login, this.message, email);
    }

}
