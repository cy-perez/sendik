import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'sendik-not-found-page',
  imports: [RouterLink, TranslocoPipe],
  templateUrl: './not-found-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotFoundPage {}
