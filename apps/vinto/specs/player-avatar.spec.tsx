import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { PlayerAvatar } from '../src/app/components/presentational/player-avatar';

describe('PlayerAvatar Component', () => {
  describe('Current Player Indicator', () => {
    it('should render mobile current player indicator when isCurrentPlayer is true', () => {
      const { getByTestId } = render(
        <PlayerAvatar
          playerName="You"
          isCurrentPlayer={true}
          isCoalitionMember={false}
          isCoalitionLeader={false}
        />
      );
      const mobileIndicator = getByTestId('active-player-indicator-mobile');
      expect(mobileIndicator).toBeTruthy();
    });

    it('should render desktop current player indicator when isCurrentPlayer is true', () => {
      const { getByTestId } = render(
        <PlayerAvatar
          playerName="You"
          isCurrentPlayer={true}
          isCoalitionMember={false}
          isCoalitionLeader={false}
        />
      );
      const desktopIndicator = getByTestId('active-player-indicator-desktop');
      expect(desktopIndicator).toBeTruthy();
    });

    it('should not have active player indicators when isCurrentPlayer is false', () => {
      const { queryByTestId } = render(
        <PlayerAvatar
          playerName="Michelangelo"
          isCurrentPlayer={false}
          isCoalitionMember={false}
          isCoalitionLeader={false}
        />
      );
      expect(queryByTestId('active-player-indicator-mobile')).toBeNull();
      expect(queryByTestId('active-player-indicator-desktop')).toBeNull();
    });
  });

  describe('Border and Text Color', () => {
    it('should have accent border when isCurrentPlayer is true (mobile)', () => {
      const { getByTestId } = render(
        <PlayerAvatar
          playerName="You"
          isCurrentPlayer={true}
          isCoalitionMember={false}
          isCoalitionLeader={false}
        />
      );
      const mobileIndicator = getByTestId('active-player-indicator-mobile');
      expect(mobileIndicator.className).toContain('border-accent');
    });

    it('should have primary border when isCurrentPlayer is false (mobile)', () => {
      const { container } = render(
        <PlayerAvatar
          playerName="Michelangelo"
          isCurrentPlayer={false}
          isCoalitionMember={false}
          isCoalitionLeader={false}
        />
      );
      const mobileBox = container.querySelector('.md\\:hidden');
      expect(mobileBox?.className).toContain('border-primary');
    });

    it('should have accent text color when isCurrentPlayer is true (mobile)', () => {
      const { getByTestId } = render(
        <PlayerAvatar
          playerName="You"
          isCurrentPlayer={true}
          isCoalitionMember={false}
          isCoalitionLeader={false}
        />
      );
      const mobileIndicator = getByTestId('active-player-indicator-mobile');
      const nameText = mobileIndicator.querySelector('.text-xs');
      expect(nameText?.className).toContain('text-accent');
    });

    it('should have secondary text color when isCurrentPlayer is false (mobile)', () => {
      const { container } = render(
        <PlayerAvatar
          playerName="Michelangelo"
          isCurrentPlayer={false}
          isCoalitionMember={false}
          isCoalitionLeader={false}
        />
      );
      const mobileBox = container.querySelector('.md\\:hidden');
      const nameText = mobileBox?.querySelector('.text-xs');
      expect(nameText?.className).toContain('text-secondary');
    });

    it('should have accent border when isCurrentPlayer is true (desktop)', () => {
      const { getByTestId } = render(
        <PlayerAvatar
          playerName="You"
          isCurrentPlayer={true}
          isCoalitionMember={false}
          isCoalitionLeader={false}
        />
      );
      const desktopIndicator = getByTestId('active-player-indicator-desktop');
      const nameBox = desktopIndicator.querySelector('.border-accent');
      expect(nameBox).toBeTruthy();
    });

    it('should have primary border when isCurrentPlayer is false (desktop)', () => {
      const { container } = render(
        <PlayerAvatar
          playerName="Michelangelo"
          isCurrentPlayer={false}
          isCoalitionMember={false}
          isCoalitionLeader={false}
        />
      );
      const desktopArea = container.querySelector('.md\\:flex.md\\:flex-col');
      const nameBox = desktopArea?.querySelector('.border-primary');
      expect(nameBox).toBeTruthy();
    });
  });

  describe('Coalition Member Badge', () => {
    it('should render coalition member badge when isCoalitionMember is true', () => {
      const { container } = render(
        <PlayerAvatar
          playerName="Donatello"
          isCurrentPlayer={false}
          isCoalitionMember={true}
          isCoalitionLeader={false}
        />
      );
      const badge = container.querySelector('.bg-info');
      expect(badge).toBeTruthy();
      expect(badge?.textContent).toContain('Team');
    });

    it('should not render coalition member badge when isCoalitionMember is false', () => {
      const { container } = render(
        <PlayerAvatar
          playerName="Donatello"
          isCurrentPlayer={false}
          isCoalitionMember={false}
          isCoalitionLeader={false}
        />
      );
      const badge = container.querySelector('.bg-info');
      expect(badge).toBeNull();
    });

    it('should have Users icon in coalition member badge', () => {
      const { container } = render(
        <PlayerAvatar
          playerName="Donatello"
          isCurrentPlayer={false}
          isCoalitionMember={true}
          isCoalitionLeader={false}
        />
      );
      const badge = container.querySelector('.bg-info');
      const icon = badge?.querySelector('svg');
      expect(icon).toBeTruthy();
    });

    it('should have title attribute on coalition member badge', () => {
      const { container } = render(
        <PlayerAvatar
          playerName="Donatello"
          isCurrentPlayer={false}
          isCoalitionMember={true}
          isCoalitionLeader={false}
        />
      );
      const badge = container.querySelector('.bg-info');
      expect(badge?.getAttribute('title')).toBe('Coalition Member');
    });
  });

  describe('Coalition Leader Badge', () => {
    it('should render coalition leader badge when isCoalitionLeader is true (desktop)', () => {
      const { container } = render(
        <PlayerAvatar
          playerName="Raphael"
          isCurrentPlayer={false}
          isCoalitionMember={false}
          isCoalitionLeader={true}
        />
      );
      const desktopArea = container.querySelector('.md\\:flex.md\\:flex-col');
      const badge = desktopArea?.querySelector('.bg-warning');
      expect(badge).toBeTruthy();
      expect(badge?.textContent).toContain('Leader');
    });

    it('should render coalition leader badge when isCoalitionLeader is true (mobile)', () => {
      const { container } = render(
        <PlayerAvatar
          playerName="Raphael"
          isCurrentPlayer={false}
          isCoalitionMember={false}
          isCoalitionLeader={true}
        />
      );
      const mobileArea = container.querySelector('.md\\:hidden');
      const badge = mobileArea?.querySelector('.bg-warning');
      expect(badge).toBeTruthy();
    });

    it('should not render coalition leader badge when isCoalitionLeader is false', () => {
      const { container } = render(
        <PlayerAvatar
          playerName="Raphael"
          isCurrentPlayer={false}
          isCoalitionMember={false}
          isCoalitionLeader={false}
        />
      );
      const badges = container.querySelectorAll('.bg-warning');
      expect(badges.length).toBe(0);
    });

    it('should have Crown icon in coalition leader badge (desktop)', () => {
      const { container } = render(
        <PlayerAvatar
          playerName="Raphael"
          isCurrentPlayer={false}
          isCoalitionMember={false}
          isCoalitionLeader={true}
        />
      );
      const desktopArea = container.querySelector('.md\\:flex.md\\:flex-col');
      const badge = desktopArea?.querySelector('.bg-warning');
      const icon = badge?.querySelector('svg');
      expect(icon).toBeTruthy();
    });

    it('should have Crown icon in coalition leader badge (mobile)', () => {
      const { container } = render(
        <PlayerAvatar
          playerName="Raphael"
          isCurrentPlayer={false}
          isCoalitionMember={false}
          isCoalitionLeader={true}
        />
      );
      const mobileArea = container.querySelector('.md\\:hidden');
      const badge = mobileArea?.querySelector('.bg-warning');
      const icon = badge?.querySelector('svg');
      expect(icon).toBeTruthy();
    });

    it('should have title attribute on coalition leader badge (desktop)', () => {
      const { container } = render(
        <PlayerAvatar
          playerName="Raphael"
          isCurrentPlayer={false}
          isCoalitionMember={false}
          isCoalitionLeader={true}
        />
      );
      const desktopArea = container.querySelector('.md\\:flex.md\\:flex-col');
      const badge = desktopArea?.querySelector('.bg-warning');
      expect(badge?.getAttribute('title')).toBe('Coalition Leader');
    });
  });

  describe('Combined States', () => {
    it('should render both current player and coalition leader indicators', () => {
      const { container, getByTestId } = render(
        <PlayerAvatar
          playerName="You"
          isCurrentPlayer={true}
          isCoalitionMember={false}
          isCoalitionLeader={true}
        />
      );
      expect(getByTestId('active-player-indicator-mobile')).toBeTruthy();
      expect(getByTestId('active-player-indicator-desktop')).toBeTruthy();
      const leaderBadge = container.querySelector('.bg-warning');
      expect(leaderBadge).toBeTruthy();
    });

    it('should render both coalition member and leader badges', () => {
      const { container } = render(
        <PlayerAvatar
          playerName="Donatello"
          isCurrentPlayer={false}
          isCoalitionMember={true}
          isCoalitionLeader={true}
        />
      );
      const memberBadge = container.querySelector('.bg-info');
      const leaderBadges = container.querySelectorAll('.bg-warning');
      expect(memberBadge).toBeTruthy();
      expect(leaderBadges.length).toBeGreaterThan(0);
    });
  });

  describe('Data Attributes', () => {
    it('should have data-player-name attribute', () => {
      const { container } = render(
        <PlayerAvatar
          playerName="Michelangelo"
          isCurrentPlayer={false}
          isCoalitionMember={false}
          isCoalitionLeader={false}
        />
      );
      const mobileArea = container.querySelector('[data-player-name]');
      expect(mobileArea?.getAttribute('data-player-name')).toBe('Michelangelo');
    });

    it('should have data-is-current-player attribute', () => {
      const { container } = render(
        <PlayerAvatar
          playerName="You"
          isCurrentPlayer={true}
          isCoalitionMember={false}
          isCoalitionLeader={false}
        />
      );
      const mobileArea = container.querySelector('[data-is-current-player]');
      expect(mobileArea?.getAttribute('data-is-current-player')).toBe('true');
    });

    it('should have data-is-current-player as false when not current player', () => {
      const { container } = render(
        <PlayerAvatar
          playerName="Raphael"
          isCurrentPlayer={false}
          isCoalitionMember={false}
          isCoalitionLeader={false}
        />
      );
      const mobileArea = container.querySelector('[data-is-current-player]');
      expect(mobileArea?.getAttribute('data-is-current-player')).toBe('false');
    });
  });

  describe('Responsive Design', () => {
    it('should have md:hidden class on mobile layout', () => {
      const { container } = render(
        <PlayerAvatar
          playerName="You"
          isCurrentPlayer={true}
          isCoalitionMember={false}
          isCoalitionLeader={false}
        />
      );
      const mobileLayout = container.querySelector('.md\\:hidden');
      expect(mobileLayout).toBeTruthy();
    });

    it('should have hidden md:flex classes on desktop layout', () => {
      const { container } = render(
        <PlayerAvatar
          playerName="You"
          isCurrentPlayer={true}
          isCoalitionMember={false}
          isCoalitionLeader={false}
        />
      );
      const desktopLayout = container.querySelector('.hidden.md\\:flex');
      expect(desktopLayout).toBeTruthy();
    });
  });

  describe('Animation', () => {
    it('should have animation style when isCurrentPlayer is true', () => {
      const { container } = render(
        <PlayerAvatar
          playerName="You"
          isCurrentPlayer={true}
          isCoalitionMember={false}
          isCoalitionLeader={false}
        />
      );
      const mobileBox = container.querySelector('[data-is-current-player="true"]');
      expect(mobileBox?.getAttribute('style')).toContain('animation');
    });

    it('should not have animation style when isCurrentPlayer is false', () => {
      const { container } = render(
        <PlayerAvatar
          playerName="Michelangelo"
          isCurrentPlayer={false}
          isCoalitionMember={false}
          isCoalitionLeader={false}
        />
      );
      const mobileBox = container.querySelector('[data-is-current-player="false"]');
      expect(mobileBox?.getAttribute('style')).toBeNull();
    });
  });
});
