import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ReportProblemButton } from '../src/app/components/buttons/report-problem-button';

describe('ReportProblemButton', () => {
  it('renders with aria-label for screen readers', () => {
    const onClick = vi.fn();
    render(<ReportProblemButton onClick={onClick} />);

    const button = screen.getByLabelText('Report a problem');
    expect(button).toBeTruthy();
    expect(button.getAttribute('aria-label')).toBe('Report a problem');
  });

  it('calls onClick when clicked', () => {
    const onClick = vi.fn();
    render(<ReportProblemButton onClick={onClick} />);

    const button = screen.getByLabelText('Report a problem');
    button.click();

    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it('renders the visible text on larger screens', () => {
    const onClick = vi.fn();
    render(<ReportProblemButton onClick={onClick} />);

    const text = screen.getByText('Report a problem');
    expect(text).toBeTruthy();
    expect(text.className).toContain('hidden');
    expect(text.className).toContain('sm:inline');
  });

  it('renders the bug icon', () => {
    const onClick = vi.fn();
    const { container } = render(<ReportProblemButton onClick={onClick} />);

    // The BugIcon from lucide-react renders an SVG
    const svg = container.querySelector('svg');
    expect(svg).toBeTruthy();
  });
});
