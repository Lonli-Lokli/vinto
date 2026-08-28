// components/TossInArea.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { Card } from './card';
import { Rank } from '@vinto/shapes';

interface TossInQueueItem {
  playerId: string;
  playerName: string;
  rank: Rank;
}

interface TossInAreaProps {
  tossInRanks: Rank[]; // Available ranks that can be tossed in
  tossInQueue: TossInQueueItem[]; // Cards that have been tossed in
  isMobile?: boolean;
}

export const TossInArea: React.FC<TossInAreaProps> = observer(
  ({ tossInRanks, tossInQueue }) => {
    const cardSize = 'sm'; // Smaller cards to show this is informational
    const textSize = 'text-2xs';
    const hasActiveTossIn = tossInRanks.length > 0 || tossInQueue.length > 0;

    return (
      <div className="flex flex-col items-center gap-1 opacity-75 min-h-[80px]">
        {hasActiveTossIn && (
          <>
            {/* Available Toss-In Ranks Section */}
            {tossInRanks.length > 0 && (
              <div className="flex flex-col items-center">
                <div
                  className={`${textSize} text-white/90 font-medium mb-0.5 drop-shadow-md`}
                >
                  Toss-In
                </div>
                <div className="flex gap-0.5">
                  {tossInRanks.map((rank, index) => (
                    <div
                      key={`rank-${rank}-${index}`}
                      className={`${textSize} px-1.5 py-0.5 bg-surface-primary/70 border border-primary/60 rounded text-primary font-bold shadow-sm`}
                    >
                      {rank}
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Toss-In Queue Section */}
            {tossInQueue.length > 0 && (
              <div className="flex flex-col items-center">
                <div
                  className={`${textSize} text-white/90 font-medium mb-0.5 drop-shadow-md`}
                >
                  Tossed ({tossInQueue.length})
                </div>
                <div className="flex flex-col gap-1">
                  {tossInQueue.map((item, index) => (
                    <div
                      key={`queue-${item.playerId}-${index}`}
                      className="flex items-center justify-between gap-2 bg-surface-primary/60 border border-primary/40 rounded px-1.5 py-0.5 shadow-sm"
                    >
                      {/* Player name */}
                      <span
                        className={`${textSize} text-secondary font-medium`}
                      >
                        {item.playerName}:
                      </span>
                      {/* Small card preview */}
                      <div className="relative flex-shrink-0">
                        <Card
                          rank={item.rank}
                          revealed={true}
                          size={cardSize}
                          selectionState="default"
                        />
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </>
        )}
      </div>
    );
  }
);
