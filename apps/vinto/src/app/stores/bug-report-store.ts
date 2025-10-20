// stores/bug-report-store.ts
import {
  makeObservable,
  observable,
  action,
  computed,
  runInAction,
} from 'mobx';
import { inject, injectable } from 'tsyringe';
import { AnimationService } from '../services/animation-service';
import { HeadlessService } from '../services/headless-service';

export type SubmitStatus = 'idle' | 'submitting' | 'success' | 'error';

@injectable()
export class BugReportStore {
  @observable email = '';
  @observable description = '';
  @observable submitStatus: SubmitStatus = 'idle';

  constructor(
    // we have to resolve our services, otherwise they will not be created
    @inject(AnimationService) _animationService: AnimationService,
    @inject(HeadlessService) _headlessService: HeadlessService
  ) {
    makeObservable(this);
  }

  @computed
  get isSubmitting(): boolean {
    return this.submitStatus === 'submitting';
  }

  @computed
  get canSubmit(): boolean {
    return this.description.trim().length > 0 && !this.isSubmitting;
  }

  @computed
  get showSuccessMessage(): boolean {
    return this.submitStatus === 'success';
  }

  @computed
  get showErrorMessage(): boolean {
    return this.submitStatus === 'error';
  }

  @action
  setEmail(email: string): void {
    this.email = email;
  }

  @action
  setDescription(description: string): void {
    this.description = description;
  }

  @action
  reset(): void {
    this.email = '';
    this.description = '';
    this.submitStatus = 'idle';
  }

  @action
  async submit(debugData: string): Promise<void> {
    if (!this.canSubmit) return;

    this.submitStatus = 'submitting';

    try {
      // Create FormData
      const formData = new FormData();
      formData.append('email', this.email);
      formData.append('description', this.description);
      formData.append('timestamp', new Date().toISOString());
      formData.append(
        'appVersion',
        process.env.NEXT_PUBLIC_VERCEL_GIT_REPO_ID || 'unknown'
      );
      formData.append('userAgent', navigator.userAgent);

      // Add debug data as a JSON attachment
      const debugBlob = new Blob([debugData], { type: 'application/json' });
      formData.append('attachment', debugBlob, 'debug-data.json');

      // Submit to formsubmit.co
      const response = await fetch(
        'https://formsubmit.co/4aa7fcc39662f7586c67799b921c4889 ',
        {
          method: 'POST',
          body: formData,
        }
      );

      runInAction(() => {
        if (response.ok) {
          this.submitStatus = 'success';
        } else {
          this.submitStatus = 'error';
        }
      });
    } catch (error) {
      console.error('Failed to submit bug report:', error);
      runInAction(() => {
        this.submitStatus = 'error';
      });
    }
  }
}
