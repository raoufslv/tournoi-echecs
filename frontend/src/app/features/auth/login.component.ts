import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth.service';
import { errorMessage } from '../../core/error-messages';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <form class="card auth" [formGroup]="form" (ngSubmit)="submit()">
      <h1>Connexion</h1>

      <label for="email">Email</label>
      <input id="email" type="email" formControlName="email" autocomplete="email" />

      <label for="password">Mot de passe</label>
      <input id="password" type="password" formControlName="password" autocomplete="current-password" />

      @if (error()) {
        <p class="error" role="alert">{{ error() }}</p>
      }

      <button type="submit" [disabled]="form.invalid || loading()">Se connecter</button>
      <p class="alt">Pas encore de compte ? <a routerLink="/register">Créer un compte</a></p>
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
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  protected submit(): void {
    if (this.form.invalid) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password).subscribe({
      next: () => this.router.navigateByUrl('/'),
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }
}
