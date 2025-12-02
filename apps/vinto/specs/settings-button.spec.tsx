import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { SettingsButton } from '../src/app/components/buttons/settings-button';

describe('SettingsButton', () => {
  it('renders with aria-label for screen readers', () => {
    const onClick = vi.fn();
    render(<SettingsButton onClick={onClick} />);

    const button = screen.getByLabelText('Settings');
    expect(button).toBeTruthy();
    expect(button.getAttribute('aria-label')).toBe('Settings');
  });

  it('renders with title attribute', () => {
    const onClick = vi.fn();
    render(<SettingsButton onClick={onClick} />);

    const button = screen.getByLabelText('Settings');
    expect(button.getAttribute('title')).toBe('Settings');
  });

  it('calls onClick when clicked', () => {
    const onClick = vi.fn();
    render(<SettingsButton onClick={onClick} />);

    const button = screen.getByLabelText('Settings');
    button.click();

    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it('applies custom className when provided', () => {
    const onClick = vi.fn();
    const customClass = 'custom-class';
    render(<SettingsButton onClick={onClick} className={customClass} />);

    const button = screen.getByLabelText('Settings');
    expect(button.className).toContain(customClass);
  });

  it('forwards ref correctly', () => {
    const onClick = vi.fn();
    const ref = { current: null } as React.RefObject<HTMLButtonElement>;
    render(<SettingsButton onClick={onClick} ref={ref} />);

    expect(ref.current).toBeInstanceOf(HTMLButtonElement);
  });
});
