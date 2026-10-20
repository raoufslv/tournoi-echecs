import { Routes } from '@angular/router';

import { authGuard } from './core/auth.guard';
import { LoginComponent } from './features/auth/login.component';
import { RegisterComponent } from './features/auth/register.component';
import { GameComponent } from './features/game/game.component';
import { LobbyComponent } from './features/lobby/lobby.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  { path: '', component: LobbyComponent, canActivate: [authGuard], pathMatch: 'full' },
  { path: 'game/:code', component: GameComponent, canActivate: [authGuard] },
  { path: '**', redirectTo: '' },
];
