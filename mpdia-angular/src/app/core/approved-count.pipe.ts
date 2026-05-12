import { Pipe, PipeTransform } from '@angular/core';
import { Indicator } from '../models/indicator.model';

@Pipe({ name: 'approvedCount', standalone: true })
export class ApprovedCountPipe implements PipeTransform {
  transform(indicators: Indicator[]): number {
    return indicators.filter(i => i.status === 'aprobado').length;
  }
}
