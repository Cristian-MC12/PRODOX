// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Activity } from '../../../models/activity.model';
import { ActivityFeedService } from '../../../services/activity-feed.service';

@Component({
  selector: 'app-activity-feed',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './activity-feed.component.html',
  styleUrl: './activity-feed.component.css'
})
export class ActivityFeedComponent implements OnInit {
  @Input({ required: true }) proyectoId!: string;
  @Input() limit: number = 10;
  
  activities: Activity[] = [];
  loading = false;
  error = false;

  constructor(private activityFeedService: ActivityFeedService) {}

  ngOnInit(): void {
    this.loadActivities();
  }

  loadActivities(): void {
    this.loading = true;
    this.error = false;

    this.activityFeedService.getProjectActivities(this.proyectoId, this.limit).subscribe({
      next: (activities) => {
        this.activities = activities;
        this.loading = false;
      },
      error: (err) => {
        console.error('Error cargando actividades:', err);
        this.activities = [];
        this.loading = false;
        this.error = true;
      }
    });
  }
  
  getIconClass(color: string): string {
    const colorMap: Record<string, string> = {
      'text-success': 'icon-success',
      'text-info': 'icon-info',
      'text-warning': 'icon-warning',
      'text-primary': 'icon-primary'
    };
    return colorMap[color] || 'icon-primary';
  }
  
  getTimeAgo(timestamp: Date): string {
    const now = new Date();
    const diff = now.getTime() - new Date(timestamp).getTime();
    
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 86400000);
    
    if (minutes < 1) return 'Ahora';
    if (minutes < 60) return `Hace ${minutes}min`;
    if (hours < 24) return `Hace ${hours}h`;
    if (days === 1) return 'Ayer';
    if (days < 7) return `Hace ${days}d`;
    
    return new Date(timestamp).toLocaleDateString('es-ES', { 
      day: 'numeric', 
      month: 'short' 
    });
  }
}
