import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';

/** Данные, передаваемые в диалог подтверждения. */
export interface ConfirmDialogData {
  message: string;
}

/**
 * Диалог подтверждения деструктивного действия (например, «Удалить задачу?»).
 * «Отмена» закрывает диалог без результата, «Удалить» — с результатом true,
 * который вызывающий компонент проверяет в afterClosed().
 */
@Component({
  selector: 'app-confirm-dialog',
  imports: [MatButtonModule, MatDialogModule],
  template: `
    <h2 mat-dialog-title>Подтверждение</h2>
    <mat-dialog-content>
      <p class="confirm-dialog__message">{{ data.message }}</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>Отмена</button>
      <button mat-raised-button color="warn" type="button" [mat-dialog-close]="true">
        Удалить
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .confirm-dialog__message {
      margin: 0;
      color: var(--mat-sys-on-surface);
      font: var(--mat-sys-body-medium);
    }
  `,
})
export class ConfirmDialogComponent {
  protected readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA);
}
