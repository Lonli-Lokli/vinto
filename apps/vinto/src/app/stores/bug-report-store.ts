// stores/bug-report-store.ts
import {
  makeObservable,
  observable,
  action,
  computed,
  runInAction,
} from 'mobx';
import { injectable } from 'tsyringe';

export type SubmitStatus = 'idle' | 'submitting' | 'success' | 'error';

@injectable()
export class BugReportStore {
  @observable email = '';
  @observable description = '';
  @observable submitStatus: SubmitStatus = 'idle';

  constructor() {
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
      formData.append('userAgent', navigator.userAgent);

      // Add debug data as a JSON attachment
      const debugBlob = new Blob([debugData], { type: 'application/json' });
      formData.append('attachment', debugBlob, 'debug-data.json');

      // Submit to formsubmit.co
      const response = await fetch(
        'https://formsubmit.co/lonli.lokli@gmail.com',
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
