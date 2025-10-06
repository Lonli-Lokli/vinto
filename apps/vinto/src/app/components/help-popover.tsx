// components/help-popover.tsx
'use client';

import React, { useState } from 'react';
import { ArrowContainer, Popover, PopoverState } from 'react-tiny-popover';
import { Rank } from '../shapes';
import { getCardHelpText } from '../constants/game-setup';
import { HelpButton } from './ui/button';

interface HelpPopoverProps {
  title: string;
  content?: string;
  rank?: Rank;
}

export const HelpPopover: React.FC<HelpPopoverProps> = ({
  title,
  content,
  rank,
}) => {
  const [showHelp, setShowHelp] = useState(false);

  const helpText = rank ? getCardHelpText(rank) : content;

  const handleToggle = () => {
    setShowHelp(!showHelp);
  };

  const helpContent = (
    <div
      className="bg-white border border-gray-300 rounded p-3 max-w-sm shadow-lg"
      style={{ zIndex: 9999 }}
    >
      <div className="text-sm text-gray-700 space-y-2">
        <p className="font-semibold text-gray-800">{title}</p>
        <div className="text-xs whitespace-pre-line">{helpText}</div>
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
          arrowColor={'white'}
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
      <HelpButton onClick={handleToggle} />
    </Popover>
  );
};
