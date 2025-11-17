// components/help-popover.tsx
'use client';

import React, { useState } from 'react';
import { ArrowContainer, Popover, PopoverState } from 'react-tiny-popover';
import { HelpButton } from '../buttons';
import { getCardHelpText, Rank } from '@vinto/shapes';

interface HelpPopoverProps {
  title: string;
  content?: string | React.ReactNode;
  rank?: Rank;
  showPulseUntilOpen?: boolean;
}

export const HelpPopover: React.FC<HelpPopoverProps> = ({
  title,
  content,
  rank,
  showPulseUntilOpen = false,
}) => {
  const [showHelp, setShowHelp] = useState(false);
  const [hasBeenOpened, setHasBeenOpened] = useState(false);

  const helpText = rank ? getCardHelpText(rank) : content;
  const isStringContent = typeof helpText === 'string';

  const handleToggle = () => {
    if (!showHelp && !hasBeenOpened) {
      setHasBeenOpened(true);
    }
    setShowHelp(!showHelp);
  };

  const shouldShowPulse = showPulseUntilOpen && !hasBeenOpened;

  const helpContent = (
    <div
      className="bg-surface-primary border border-primary rounded p-3 shadow-lg"
      style={{ 
        zIndex: 9999,
        maxWidth: isStringContent ? '24rem' : '40rem',
      }}
    >
      <div className="text-sm text-secondary space-y-2">
        {isStringContent ? (
          <>
            <p className="font-semibold text-primary">{title}</p>
            <div className="text-xs whitespace-pre-line">{helpText}</div>
          </>
        ) : (
          helpText
        )}
      </div>
    </div>
  );

  return (
    <Popover
      isOpen={showHelp}
      positions={['top', 'right', 'bottom', 'left']}
      align="center"
      padding={8}
      reposition={true}
      containerStyle={{ zIndex: '9999' }}
      content={({ position, childRect, popoverRect }: PopoverState) => (
        <ArrowContainer
          position={position}
          childRect={childRect}
          popoverRect={popoverRect}
          arrowColor="rgb(var(--color-surface-primary))"
          arrowSize={8}
          arrowStyle={{ opacity: 1 }}
          className="popover-arrow-container"
          arrowClassName="popover-arrow"
        >
          {helpContent}
        </ArrowContainer>
      )}
      onClickOutside={() => setShowHelp(false)}
    >
      <div className="relative inline-flex items-center gap-1">
        {shouldShowPulse && (
          <div className="animate-bounce text-base pointer-events-none">
            👉
          </div>
        )}
        <HelpButton onClick={handleToggle} />
      </div>
    </Popover>
  );
};
