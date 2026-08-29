// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivityFeedComponent } from './activity-feed.component';
import { ActivityFeedService } from '../../../services/activity-feed.service';
import { of, throwError } from 'rxjs';

describe('ActivityFeedComponent', () => {
  let component: ActivityFeedComponent;
  let fixture: ComponentFixture<ActivityFeedComponent>;
  let mockActivityFeedService: jasmine.SpyObj<ActivityFeedService>;

  beforeEach(async () => {
    mockActivityFeedService = jasmine.createSpyObj('ActivityFeedService', ['getProjectActivities']);
    mockActivityFeedService.getProjectActivities.and.returnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [ActivityFeedComponent],
      providers: [
        { provide: ActivityFeedService, useValue: mockActivityFeedService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ActivityFeedComponent);
    component = fixture.componentInstance;
    component.proyectoId = 'test-proyecto-id';
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should call activity feed service on init', () => {
    expect(mockActivityFeedService.getProjectActivities).toHaveBeenCalledWith('test-proyecto-id', 10);
  });

  it('should format time ago correctly', () => {
    const now = new Date();
    const oneHourAgo = new Date(now.getTime() - 3600000);

    expect(component.getTimeAgo(oneHourAgo)).toContain('Hace 1h');
  });

  it('debe distinguir error de vacío (no mostrar "sin actividad" cuando falló la carga)', () => {
    mockActivityFeedService.getProjectActivities.and.returnValue(throwError(() => new Error('falló')));

    component.loadActivities();

    expect(component.error).toBeTrue();
    expect(component.activities).toEqual([]);

    fixture.detectChanges();
    const compiled = fixture.nativeElement;
    expect(compiled.textContent).toContain('No se pudo cargar la actividad');
  });
});
