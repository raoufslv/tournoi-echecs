import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth.service';
import { errorMessage } from '../../core/error-messages';

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <form class="card auth" [formGroup]="form" (ngSubmit)="submit()">
      <h1>Créer un compte</h1>

      <label for="displayName">Pseudo</label>
      <input id="displayName" type="text" formControlName="displayName" maxlength="50" />

      <label for="email">Email</label>
      <input id="email" type="email" formControlName="email" autocomplete="email" />

      <label for="password">Mot de passe (8 à 72 caractères)</label>
      <input id="password" type="password" formControlName="password" autocomplete="new-password" />
      @if (form.controls.password.touched && form.controls.password.invalid) {
        <p class="field-error">Le mot de passe doit contenir entre 8 et 72 caractères.</p>
      }

      @if (error()) {
        <p class="error" role="alert">{{ error() }}</p>
      }

      <button type="submit" [disabled]="form.invalid || loading()">Créer mon compte</button>
      <p class="alt">Déjà inscrit ? <a routerLink="/login">Se connecter</a></p>
    </form>
  `,
  styles: `
    .auth {
      max-width: 400px;
      margin: 0 auto;
    }
    .alt {
      margin: 1rem 0 0;
      font-size: 0.9rem;
      color: var(--muted);
    }
  `,
})
export class RegisterComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    displayName: ['', [Validators.required, Validators.maxLength(50)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
  });

  protected submit(): void {
    if (this.form.invalid) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const { email, password, displayName } = this.form.getRawValue();
    this.auth.register(email, password, displayName).subscribe({
      next: () => this.router.navigateByUrl('/'),
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }
}
