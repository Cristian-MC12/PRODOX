// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-auth',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  styles: [`
    * {
      margin: 0;
      padding: 0;
      box-sizing: border-box;
    }

    :host {
      display: block;
      width: 100%;
      overflow-x: hidden;
    }

    @keyframes floatDots {
      0%, 100% { transform: translateY(0px) scale(1); opacity: 0.6; }
      50% { transform: translateY(-20px) scale(1.1); opacity: 1; }
    }

    @keyframes glowBorder {
      0%, 100% { 
        box-shadow: 
          0 20px 60px rgba(0, 0, 0, 0.7),
          0 0 100px rgba(99, 102, 241, 0.15),
          inset 0 1px 0 rgba(255, 255, 255, 0.05),
          0 0 40px rgba(99, 102, 241, 0.3);
      }
      50% { 
        box-shadow: 
          0 20px 80px rgba(0, 0, 0, 0.8),
          0 0 120px rgba(139, 92, 246, 0.4),
          inset 0 1px 0 rgba(255, 255, 255, 0.1),
          0 0 60px rgba(139, 92, 246, 0.6);
      }
    }

    .login-container {
      min-height: 100vh;
      background: url('/assets/images/backgrounds/BACK_LOGIN.png') no-repeat center center;
      background-size: cover;
      background-attachment: fixed;
      display: grid;
      grid-template-columns: 1fr 1fr;
      align-items: stretch;
      padding: 0;
      position: relative;
      overflow-x: hidden;
      overflow-y: auto;
    }

    @media (max-width: 768px) {
      .login-container {
        background-attachment: scroll;
        background-position: center center;
        display: flex;
        flex-direction: column;
      }
    }

    /* .floating-dot y los pseudo-elementos del login-container son el mismo
       punto decorativo (mismo tamaño/color/sombra/animación parametrizados
       por custom properties); se comparte una única regla base para no
       repetir las mismas 6 declaraciones tres veces (mismo resultado
       visual, menos CSS). */
    .floating-dot,
    .login-container::before,
    .login-container::after {
      position: absolute;
      border-radius: 50%;
      width: var(--s);
      height: var(--s);
      background: radial-gradient(circle, var(--c) 0%, transparent 70%);
      box-shadow: 0 0 var(--g) var(--sc);
      animation: floatDots var(--d) ease-in-out infinite var(--dl);
    }

    .floating-dot { pointer-events: none; }
    .login-container::before,
    .login-container::after { content: ''; }

    .login-container::before { --s: 6px; top: 20%; left: 15%; --c: rgba(167, 139, 250, 1); --sc: rgba(167, 139, 250, 0.8); --d: 4s; --dl: 0s; --g: 20px; }
    .login-container::after { --s: 8px; bottom: 30%; right: 25%; --c: rgba(99, 102, 241, 1); --sc: rgba(99, 102, 241, 0.9); --d: 5s; --dl: 1s; --g: 25px; }

    .floating-dot:nth-child(1) { --s: 5px; top: 15%; left: 45%; --c: rgba(167, 139, 250, 1); --sc: rgba(167, 139, 250, 0.8); --d: 6s; --dl: 0.5s; --g: 18px; }
    .floating-dot:nth-child(2) { --s: 7px; top: 60%; left: 30%; --c: rgba(139, 92, 246, 1); --sc: rgba(139, 92, 246, 0.9); --d: 7s; --dl: 1.5s; --g: 22px; }
    .floating-dot:nth-child(3) { --s: 6px; top: 40%; right: 15%; --c: rgba(99, 102, 241, 1); --sc: rgba(99, 102, 241, 0.85); --d: 5.5s; --dl: 2s; --g: 20px; }
    .floating-dot:nth-child(4) { --s: 4px; top: 75%; left: 50%; --c: rgba(196, 181, 253, 1); --sc: rgba(196, 181, 253, 0.9); --d: 6.5s; --dl: 2.5s; --g: 15px; }
    .floating-dot:nth-child(5) { --s: 5px; bottom: 20%; left: 20%; --c: rgba(124, 58, 237, 1); --sc: rgba(124, 58, 237, 0.85); --d: 7.5s; --dl: 3s; --g: 19px; }
    .floating-dot:nth-child(6) { --s: 6px; top: 25%; right: 35%; --c: rgba(109, 40, 217, 1); --sc: rgba(109, 40, 217, 0.9); --d: 6s; --dl: 0.8s; --g: 21px; }

    .left-section {
      padding: 3rem 2rem 3rem 4rem;
      color: white;
      display: flex;
      flex-direction: column;
      justify-content: center;
      min-height: 100vh;
    }

    .logo-wrapper {
      margin-bottom: 3rem;
    }

    .logo {
      width: 300px;
      height: auto;
      display: block;
    }

    .tagline {
      font-size: 1.125rem;
      line-height: 1.7;
      font-weight: 300;
      color: rgba(255, 255, 255, 0.95);
      margin-bottom: 1.5rem;
    }

    .highlight {
      color: #a78bfa;
      font-weight: 600;
    }

    .divider-line {
      width: 60px;
      height: 2px;
      background: linear-gradient(90deg, #a78bfa 0%, transparent 100%);
      margin-bottom: 2rem;
    }

    .copyright {
      font-size: 0.6875rem;
      color: rgba(255, 255, 255, 0.5);
      margin-top: auto;
    }

    .right-section {
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 3rem;
      min-height: 100vh;
    }

    .login-card {
      width: 100%;
      max-width: 650px;
      background: linear-gradient(135deg, rgba(20, 27, 50, 0.95) 0%, rgba(15, 23, 45, 0.95) 100%);
      backdrop-filter: blur(30px);
      border: 1px solid rgba(99, 102, 241, 0.3);
      border-radius: 28px;
      padding: 2rem 3rem;
      box-shadow: 
        0 20px 60px rgba(0, 0, 0, 0.7),
        0 0 100px rgba(99, 102, 241, 0.15),
        inset 0 1px 0 rgba(255, 255, 255, 0.05);
      position: relative;
      animation: glowBorder 3s ease-in-out infinite;
    }

    .header {
      margin-bottom: 1.5rem;
      padding-top: 0;
    }

    .title {
      font-size: 1.75rem;
      font-weight: 700;
      color: #ffffff;
      margin-bottom: 0.5rem;
      line-height: 1.2;
      padding-right: 180px;
    }

    .title-highlight {
      color: #a78bfa;
      font-weight: 700;
    }

    .subtitle {
      font-size: 0.875rem;
      color: rgba(255, 255, 255, 0.7);
      line-height: 1.5;
      font-weight: 400;
      margin-bottom: 0;
    }

    .security-badge {
      position: absolute;
      top: 2rem;
      right: 3rem;
      display: flex;
      align-items: flex-start;
      gap: 0.5rem;
      background: rgba(79, 70, 229, 0.15);
      border: 1px solid rgba(99, 102, 241, 0.3);
      border-radius: 10px;
      padding: 0.5rem 0.75rem;
      z-index: 10;
    }

    .badge-icon {
      font-size: 1rem;
      color: #818cf8;
      line-height: 1;
      margin-top: 0.125rem;
    }

    .badge-text h4 {
      font-size: 0.75rem;
      font-weight: 600;
      color: #ffffff;
      margin: 0 0 0.125rem 0;
      line-height: 1.2;
    }

    .badge-text p {
      font-size: 0.625rem;
      color: rgba(255, 255, 255, 0.6);
      margin: 0;
      line-height: 1.2;
    }

    .form-group {
      margin-bottom: 1.25rem;
    }

    .form-label {
      display: block;
      font-size: 0.875rem;
      font-weight: 600;
      color: #ffffff;
      margin-bottom: 0.5rem;
    }

    .input-wrapper {
      position: relative;
    }

    .input-icon {
      position: absolute;
      left: 1.125rem;
      top: 50%;
      transform: translateY(-50%);
      color: rgba(255, 255, 255, 0.4);
      font-size: 1.0625rem;
      pointer-events: none;
    }

    .form-input {
      width: 100%;
      padding: 0.875rem 1rem 0.875rem 3rem;
      background: rgba(20, 27, 50, 0.6);
      border: 1px solid rgba(99, 102, 241, 0.25);
      border-radius: 12px;
      color: #ffffff;
      font-size: 0.9375rem;
      outline: none;
      transition: all 0.3s ease;
      font-family: inherit;
    }

    .form-input:-webkit-autofill,
    .form-input:-webkit-autofill:hover,
    .form-input:-webkit-autofill:focus,
    .form-input:-webkit-autofill:active {
      -webkit-box-shadow: 0 0 0 1000px rgba(20, 27, 50, 0.6) inset !important;
      -webkit-text-fill-color: #ffffff !important;
      transition: background-color 5000s ease-in-out 0s;
      border: 1px solid rgba(99, 102, 241, 0.25);
    }

    select.form-input {
      appearance: none;
      -webkit-appearance: none;
      -moz-appearance: none;
      background-color: rgba(20, 27, 50, 0.6);
      background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='8' viewBox='0 0 12 8'%3E%3Cpath fill='%23a78bfa' d='M1 1l5 5 5-5' stroke='%23a78bfa' stroke-width='1.5' fill='none' stroke-linecap='round' stroke-linejoin='round'/%3E%3C/svg%3E");
      background-repeat: no-repeat;
      background-position: right 1.25rem center;
      background-size: 12px 8px;
      padding-right: 3rem;
      cursor: pointer;
      font-weight: 500;
      transition: all 0.3s ease;
    }

    select.form-input::-ms-expand {
      display: none;
    }

    select.form-input:hover {
      background-color: rgba(20, 27, 50, 0.75);
      border-color: rgba(129, 140, 248, 0.4);
    }

    select.form-input:focus {
      background-color: rgba(20, 27, 50, 0.8);
    }

    select.form-input option {
      background-color: #1e293b;
      color: #f1f5f9;
      padding: 0.75rem;
      font-size: 0.9375rem;
      font-weight: 500;
    }

    .form-input::placeholder {
      color: rgba(255, 255, 255, 0.3);
    }

    .form-input:focus {
      background: rgba(20, 27, 50, 0.8);
      border-color: #818cf8;
      box-shadow: 0 0 0 3px rgba(129, 140, 248, 0.15);
    }

    .toggle-password {
      position: absolute;
      right: 1.125rem;
      top: 50%;
      transform: translateY(-50%);
      background: none;
      border: none;
      color: rgba(255, 255, 255, 0.5);
      cursor: pointer;
      padding: 0.375rem;
      font-size: 1.0625rem;
      line-height: 1;
      transition: color 0.2s ease;
    }

    .toggle-password:hover {
      color: #a78bfa;
    }

    .forgot-password {
      text-align: right;
      margin-top: 0.5rem;
    }

    .forgot-link {
      font-size: 0.8125rem;
      color: #a78bfa;
      text-decoration: none;
      font-weight: 500;
      transition: color 0.2s ease;
    }

    .forgot-link:hover {
      color: #c4b5fd;
    }

    .submit-button {
      width: 100%;
      padding: 0.9375rem;
      background: linear-gradient(135deg, #7c3aed 0%, #6366f1 100%);
      border: none;
      border-radius: 12px;
      color: #ffffff;
      font-size: 1rem;
      font-weight: 700;
      cursor: pointer;
      transition: all 0.3s ease;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.625rem;
      margin-top: 1.5rem;
      box-shadow: 
        0 8px 24px rgba(124, 58, 237, 0.4),
        0 4px 12px rgba(99, 102, 241, 0.3);
    }

    .submit-button:hover:not(:disabled) {
      transform: translateY(-2px);
      box-shadow: 
        0 12px 32px rgba(124, 58, 237, 0.5),
        0 6px 16px rgba(99, 102, 241, 0.4);
    }

    .submit-button:active:not(:disabled) {
      transform: translateY(0);
    }

    .submit-button:disabled {
      opacity: 0.7;
      cursor: not-allowed;
    }

    .button-icon {
      font-size: 1.25rem;
      line-height: 1;
    }

    .divider {
      text-align: center;
      font-size: 0.8125rem;
      color: rgba(255, 255, 255, 0.5);
      margin: 1.5rem 0 1.25rem;
      font-weight: 400;
    }

    .google-button {
      width: 100%;
      padding: 0.9375rem;
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid rgba(255, 255, 255, 0.15);
      border-radius: 12px;
      color: #ffffff;
      font-size: 0.9375rem;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.3s ease;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.75rem;
    }

    .google-button:hover {
      background: rgba(255, 255, 255, 0.08);
      border-color: rgba(255, 255, 255, 0.25);
      transform: translateY(-1px);
    }

    .google-icon {
      width: 20px;
      height: 20px;
    }

    .footer {
      text-align: center;
      font-size: 0.875rem;
      color: rgba(255, 255, 255, 0.6);
      margin-top: 1.5rem;
      font-weight: 400;
    }

    .footer-link {
      color: #a78bfa;
      text-decoration: none;
      font-weight: 600;
      transition: color 0.2s ease;
    }

    .footer-link:hover {
      color: #c4b5fd;
    }

    .error-alert {
      background: rgba(239, 68, 68, 0.15);
      border: 1px solid rgba(239, 68, 68, 0.35);
      border-radius: 10px;
      padding: 0.875rem 1rem;
      color: #fca5a5;
      font-size: 0.875rem;
      margin-bottom: 1.25rem;
      line-height: 1.4;
      font-weight: 500;
    }

    /* @keyframes spin ahora vive en styles.scss (compartido con
       configuracion.component.ts, que definía el mismo keyframe). */
    .spinner {
      width: 20px;
      height: 20px;
      border: 2px solid rgba(255, 255, 255, 0.3);
      border-top-color: #ffffff;
      border-radius: 50%;
      animation: spin 0.6s linear infinite;
    }

    @media (max-width: 1024px) {
      .login-container {
        grid-template-columns: 1fr;
        display: flex;
        flex-direction: column;
      }

      .left-section {
        display: none;
      }

      .right-section {
        padding: 2rem;
        min-height: 100vh;
      }

      .login-card {
        max-width: 600px;
        margin: auto;
      }
    }

    @media (max-width: 768px) {
      .login-container {
        grid-template-columns: 1fr;
        padding: 0;
        display: flex;
        flex-direction: column;
      }

      .left-section {
        display: none;
      }

      .right-section {
        padding: 2rem 1.5rem;
        min-height: 100vh;
      }

      .login-card {
        padding: 2.5rem 2rem;
        max-width: 100%;
        border-radius: 24px;
        margin: 0;
      }

      .security-badge {
        position: static;
        margin-bottom: 2rem;
        flex-direction: row;
        gap: 1rem;
      }

      .badge-text h4 {
        font-size: 0.875rem;
      }

      .badge-text p {
        font-size: 0.75rem;
      }

      .title {
        font-size: 1.75rem;
      }

      .subtitle {
        font-size: 0.9375rem;
      }

      .form-label {
        font-size: 0.875rem;
      }

      .form-input {
        font-size: 0.9375rem;
        padding: 0.9375rem 1rem 0.9375rem 3rem;
      }

      .submit-button {
        font-size: 1rem;
        padding: 1rem;
      }

      .google-button {
        font-size: 0.9375rem;
        padding: 1rem;
      }

      .footer {
        font-size: 0.875rem;
      }

      .floating-dot:nth-child(2),
      .floating-dot:nth-child(4),
      .floating-dot:nth-child(6) {
        display: none;
      }
    }

    @media (max-width: 480px) {
      .right-section {
        padding: 1.5rem 1rem;
        min-height: auto;
        padding-top: 2rem;
        padding-bottom: 2rem;
      }

      .login-card {
        padding: 2rem 1.5rem;
        border-radius: 20px;
      }

      .security-badge {
        padding: 0.875rem 1rem;
        flex-direction: column;
        text-align: center;
      }

      .badge-icon {
        font-size: 1.25rem;
      }

      .title {
        font-size: 1.5rem;
      }

      .subtitle {
        font-size: 0.875rem;
        line-height: 1.5;
      }

      .form-group {
        margin-bottom: 1.25rem;
      }

      .form-label {
        font-size: 0.8125rem;
        margin-bottom: 0.5rem;
      }

      .form-input {
        font-size: 0.875rem;
        padding: 0.875rem 0.875rem 0.875rem 2.75rem;
      }

      .input-icon {
        font-size: 1rem;
        left: 1rem;
      }

      .toggle-password {
        right: 0.875rem;
      }

      .submit-button {
        font-size: 0.9375rem;
        padding: 0.9375rem;
        margin-top: 1.5rem;
      }

      .button-icon {
        font-size: 1.125rem;
      }

      .divider {
        margin: 1.5rem 0 1.25rem;
        font-size: 0.8125rem;
      }

      .google-button {
        font-size: 0.875rem;
        padding: 0.9375rem;
      }

      .google-icon {
        width: 18px;
        height: 18px;
      }

      .footer {
        font-size: 0.8125rem;
        margin-top: 1.5rem;
      }

      .floating-dot {
        display: none;
      }

      .login-container::before,
      .login-container::after {
        display: none;
      }
    }

    @media (max-width: 360px) {
      .right-section {
        padding: 1rem;
      }

      .login-card {
        padding: 1.5rem 1rem;
      }

      .title {
        font-size: 1.375rem;
      }

      .subtitle {
        font-size: 0.8125rem;
      }

      .security-badge {
        padding: 0.75rem 0.875rem;
      }

      .badge-text h4 {
        font-size: 0.75rem;
      }

      .badge-text p {
        font-size: 0.6875rem;
      }
    }
  `],
  template: `
    <div class="login-container">
      <!-- Puntos flotantes animados -->
      <div class="floating-dot"></div>
      <div class="floating-dot"></div>
      <div class="floating-dot"></div>
      <div class="floating-dot"></div>
      <div class="floating-dot"></div>
      <div class="floating-dot"></div>

      <!-- Sección Izquierda - Logo y Texto -->
      <div class="left-section">
        <div class="logo-wrapper">
          <img src="/assets/images/logos/Logo-PRODOX-AI.jpg" alt="PRODOX" class="logo">
        </div>
        <p class="tagline">
          Medición de productividad<br>
          de equipos ágiles con el poder<br>
          de la <span class="highlight">IA generativa</span>.
        </p>
        <div class="divider-line"></div>
        <p class="copyright">
          © 2026 PRODOX by Teams. Todos los derechos reservados.
        </p>
      </div>

      <!-- Sección Derecha - Formulario -->
      <div class="right-section">
        <div class="login-card">
        <!-- Badge de Seguridad -->
        <div class="security-badge">
          <i class="bi bi-shield-check badge-icon"></i>
          <div class="badge-text">
            <h4>Conexión segura</h4>
            <p>Tus datos están protegidos</p>
          </div>
        </div>

        <!-- Header -->
        <div class="header">
          @if (tab === 'login') {
            <h1 class="title">
              Bienvenido <span class="title-highlight">de nuevo</span>
            </h1>
            <p class="subtitle">
              Inicia sesión para continuar gestionando<br>
              la productividad de tu equipo ágil.
            </p>
          } @else if (tab === 'register') {
            <h1 class="title">
              Crear <span class="title-highlight">cuenta</span>
            </h1>
            <p class="subtitle">
              Regístrate para comenzar a medir<br>
              la productividad de tu equipo.
            </p>
          } @else {
            <h1 class="title">
              Recuperar <span class="title-highlight">contraseña</span>
            </h1>
            <p class="subtitle">
              Ingresá tu correo y te enviaremos instrucciones<br>
              para restablecer tu contraseña.
            </p>
          }
        </div>

        <!-- Error Alert -->
        @if (errorMsg) {
          <div class="error-alert">
            {{ errorMsg }}
          </div>
        }

        <!-- Formulario: recuperar contraseña -->
        @if (tab === 'forgot') {
          <form [formGroup]="forgotForm" (ngSubmit)="sendForgotPassword()">
            <div class="form-group">
              <label class="form-label">Correo electrónico</label>
              <div class="input-wrapper">
                <i class="bi bi-envelope input-icon"></i>
                <input
                  type="email"
                  class="form-input"
                  formControlName="email"
                  placeholder="ejemplo@correo.com"
                  autocomplete="email">
              </div>
            </div>

            @if (forgotMsg) {
              <div class="error-alert" style="background: rgba(34, 197, 94, 0.15); border-color: rgba(34, 197, 94, 0.35); color: #86efac;">
                {{ forgotMsg }}
              </div>
            }

            <button type="submit" class="submit-button" [disabled]="forgotLoading">
              @if (forgotLoading) {
                <div class="spinner"></div>
              } @else {
                <span>Enviar instrucciones</span>
                <i class="bi bi-arrow-right button-icon"></i>
              }
            </button>
          </form>
        }

        <!-- Form: login / registro -->
        @if (tab !== 'forgot') {
        <form [formGroup]="form" (ngSubmit)="submit()">
          <!-- Nombre (solo para registro) -->
          @if (tab === 'register') {
            <div class="form-group">
              <label class="form-label">Nombre completo</label>
              <div class="input-wrapper">
                <i class="bi bi-person input-icon"></i>
                <input
                  type="text"
                  class="form-input"
                  formControlName="nombre"
                  placeholder="Ej: Juan Pérez"
                  autocomplete="name">
              </div>
            </div>
          }

          <!-- Email -->
          <div class="form-group">
            <label class="form-label">Correo electrónico</label>
            <div class="input-wrapper">
              <i class="bi bi-envelope input-icon"></i>
              <input 
                type="email" 
                class="form-input"
                formControlName="email"
                placeholder="ejemplo@correo.com"
                autocomplete="email">
            </div>
          </div>

          <!-- Password -->
          <div class="form-group">
            <label class="form-label">Contraseña</label>
            <div class="input-wrapper">
              <i class="bi bi-lock input-icon"></i>
              <input 
                [type]="showPassword ? 'text' : 'password'" 
                class="form-input"
                formControlName="password"
                placeholder="Ingresa tu contraseña"
                autocomplete="current-password">
              <button 
                type="button" 
                class="toggle-password"
                (click)="showPassword = !showPassword"
                [attr.aria-label]="showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'">
                <i class="bi" [class.bi-eye]="!showPassword" [class.bi-eye-slash]="showPassword"></i>
              </button>
            </div>
            @if (tab === 'login') {
              <div class="forgot-password">
                <a href="#" class="forgot-link" (click)="$event.preventDefault(); switchTab('forgot')">¿Olvidaste tu contraseña?</a>
              </div>
            }
          </div>

          <!-- Role (solo para registro) -->
          @if (tab === 'register') {
            <div class="form-group">
              <label class="form-label">Rol</label>
              <div class="input-wrapper">
                <i class="bi bi-person-badge input-icon"></i>
                <select class="form-input" formControlName="role">
                  <option value="scrum_member">💻 Desarrollador</option>
                  <option value="scrum_master">🎯 Scrum Master</option>
                </select>
              </div>
            </div>
          }

          <!-- Submit Button -->
          <button type="submit" class="submit-button" [disabled]="loading">
            @if (loading) {
              <div class="spinner"></div>
            } @else {
              <span>{{ tab === 'login' ? 'Iniciar sesión' : 'Registrarse' }}</span>
              <i class="bi bi-arrow-right button-icon"></i>
            }
          </button>
        </form>
        }

        <!-- Divider + Google: no aplica en el flujo de recuperar contraseña -->
        @if (tab !== 'forgot') {
        <p class="divider">o continúa con</p>

        <button type="button" class="google-button" (click)="loginWithGoogle()">
          <svg class="google-icon" viewBox="0 0 20 20" fill="none">
            <path d="M19.6 10.227c0-.709-.064-1.39-.182-2.045H10v3.868h5.382a4.6 4.6 0 01-1.996 3.018v2.51h3.232c1.891-1.742 2.982-4.305 2.982-7.35z" fill="#4285F4"/>
            <path d="M10 20c2.7 0 4.964-.895 6.618-2.423l-3.232-2.509c-.895.6-2.04.955-3.386.955-2.605 0-4.81-1.76-5.595-4.123H1.064v2.59A9.996 9.996 0 0010 20z" fill="#34A853"/>
            <path d="M4.405 11.9c-.2-.6-.314-1.24-.314-1.9 0-.66.114-1.3.314-1.9V5.51H1.064A9.996 9.996 0 000 10c0 1.614.386 3.14 1.064 4.49l3.34-2.59z" fill="#FBBC05"/>
            <path d="M10 3.977c1.468 0 2.786.505 3.823 1.496l2.868-2.868C14.959.99 12.695 0 10 0 6.09 0 2.71 2.24 1.064 5.51l3.34 2.59C5.19 5.736 7.395 3.977 10 3.977z" fill="#EA4335"/>
          </svg>
          Continuar con Google
        </button>
        }

        <!-- Footer -->
        <p class="footer">
          @if (tab === 'login') {
            ¿No tienes una cuenta? <a href="#" class="footer-link" (click)="$event.preventDefault(); switchTab('register')">Regístrate aquí</a>
          } @else if (tab === 'register') {
            ¿Ya tienes cuenta? <a href="#" class="footer-link" (click)="$event.preventDefault(); switchTab('login')">Inicia sesión</a>
          } @else {
            <a href="#" class="footer-link" (click)="$event.preventDefault(); switchTab('login')">Volver a iniciar sesión</a>
          }
        </p>
        </div>
      </div>
    </div>
  `
})
export class AuthComponent implements OnInit {
  tab: 'login' | 'register' | 'forgot' = 'login';
  form: FormGroup;
  loading  = false;
  errorMsg = '';
  codigoInvitacion = '';
  showPassword = false;

  forgotForm: FormGroup;
  forgotLoading = false;
  forgotMsg = '';

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute
  ) {
    this.form = this.buildForm('login');
    this.forgotForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]]
    });
  }

  ngOnInit(): void {
    // Manejar callback de OAuth2
    this.route.queryParams.subscribe(params => {
      const token = params['token'];
      const error = params['error'];

      // /invitacion redirige acá con ?tab=register cuando el usuario todavía
      // no tiene cuenta, para abrir directamente el formulario de registro
      // en vez de dejarlo en login (no cambia el comportamiento si el
      // parámetro no está presente).
      if (params['tab'] === 'register' && this.tab !== 'register') {
        this.switchTab('register');
      }

      if (token) {
        // Token recibido desde OAuth2: reutiliza el mismo mecanismo de sesión
        // que el login tradicional (misma clave de localStorage y mismo signal
        // currentUser), para que el authGuard reconozca la sesión de inmediato.
        this.authService.persistFromToken(token);
        this.redirectAfterAuth();
      } else if (error) {
        // Error en OAuth2
        this.errorMsg = 'Error al autenticar con Google. Por favor, intenta nuevamente.';
        console.error('Error OAuth:', error);
      }
    });
  }

  /**
   * Después de un login/registro/Google exitoso: si el usuario venía de
   * /invitacion (código guardado por InvitacionComponent antes de redirigir
   * acá), lo devuelve ahí para que la invitación se acepte automáticamente
   * en vez de dejarlo en la pantalla de inicio genérica.
   */
  private redirectAfterAuth(): void {
    const codigo = this.authService.getInvitacionPendiente();
    if (codigo) {
      this.router.navigate(['/invitacion'], { queryParams: { codigo } });
    } else {
      this.router.navigate(['/']);
    }
  }

  get f() { return this.form.controls; }

  switchTab(t: 'login' | 'register' | 'forgot'): void {
    this.tab = t;
    this.errorMsg = '';
    this.forgotMsg = '';
    if (t !== 'forgot') {
      this.form = this.buildForm(t);
    } else {
      this.forgotForm.reset();
    }
  }

  sendForgotPassword(): void {
    if (this.forgotForm.invalid) { this.forgotForm.markAllAsTouched(); return; }
    this.forgotLoading = true;
    this.errorMsg = '';
    this.forgotMsg = '';

    const { email } = this.forgotForm.value;
    this.authService.forgotPassword(email).subscribe({
      next: (res) => {
        this.forgotMsg = res.message;
        this.forgotLoading = false;
      },
      error: (err) => {
        // El backend responde genérico incluso si el correo no existe; un
        // error acá solo puede ser de red/validación, no "correo no encontrado".
        this.errorMsg = err?.error?.error ?? 'Error al solicitar la recuperación.';
        this.forgotLoading = false;
      }
    });
  }

  submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.loading  = true;
    this.errorMsg = '';

    const { email, password, role, nombre } = this.form.value;
    const obs = this.tab === 'login'
      ? this.authService.login({ email, password })
      : this.authService.register({ email, password, role, nombre });

    obs.subscribe({
      next:  () => this.redirectAfterAuth(),
      error: (err) => {
        this.errorMsg = err?.error?.error ?? 'Error al autenticar.';
        this.loading  = false;
      }
    });
  }

  loginWithGoogle(): void {
    this.loading = true;
    this.errorMsg = '';
    
    // Redirigir al endpoint OAuth2 de Spring Boot
    window.location.href = 'http://localhost:8080/oauth2/authorization/google';
  }

  private buildForm(tab: 'login' | 'register'): FormGroup {
    return this.fb.group({
      email:    ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(8)]],
      role:     [tab === 'register' ? '' : null, tab === 'register' ? Validators.required : []],
      nombre:   [tab === 'register' ? '' : null, tab === 'register' ? Validators.required : []]
    });
  }
}
