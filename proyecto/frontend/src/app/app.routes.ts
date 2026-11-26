import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { Shell } from './layout/shell';

export const routes: Routes = [
  {
    path: 'login',
    title: 'Iniciar sesión',
    loadComponent: () => import('./pages/login-page').then((m) => m.LoginPage),
  },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', title: 'Inicio', loadComponent: () => import('./pages/home-page').then((m) => m.HomePage) },
      { path: 'search', title: 'Buscar', loadComponent: () => import('./pages/search-page').then((m) => m.SearchPage) },
      {
        path: 'lists/new',
        title: 'Crear playlist',
        loadComponent: () => import('./pages/create-playlist-page').then((m) => m.CreatePlaylistPage),
      },
      {
        path: 'lists/:nombre',
        title: 'Playlist',
        loadComponent: () => import('./pages/playlist-page').then((m) => m.PlaylistPage),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
