// components/action-types/KingDeclaration.tsx
'use client';

import React from 'react';
import { Rank } from '../../shapes';

interface KingDeclarationProps {
  action: string;
  onDeclareAction: (rank: Rank) => void;
}

export function KingDeclaration({ action, onDeclareAction }: KingDeclarationProps) {
  return (
    <div className="bg-yellow-50 rounded-lg p-3 mb-3">
      <div className="text-center text-sm text-yellow-800">
        👑 <strong>King Declaration</strong>
        <br />
        <span className="text-xs text-yellow-600">
          Choose any card rank to execute its action
        </span>
      </div>

      <div className="text-center">
        <p className="text-xs text-gray-500 mt-2 mb-3">
          Choose any card rank to execute its action
        </p>
      </div>

      <div className="mt-3 grid grid-cols-3 gap-2">
        {/* Action cards with descriptions */}
        {[
          { rank: '7' as Rank, action: 'Peek own card', color: 'from-blue-500 to-blue-600' },
          { rank: '8' as Rank, action: 'Peek own card', color: 'from-blue-500 to-blue-600' },
          { rank: '9' as Rank, action: 'Peek opponent card', color: 'from-orange-500 to-orange-600' },
          { rank: '10' as Rank, action: 'Peek opponent card', color: 'from-orange-500 to-orange-600' },
          { rank: 'J' as Rank, action: 'Swap any 2 cards', color: 'from-purple-500 to-purple-600' },
          { rank: 'Q' as Rank, action: 'Peek 2 then swap', color: 'from-pink-500 to-pink-600' },
          { rank: 'K' as Rank, action: 'Declare action', color: 'from-yellow-500 to-yellow-600' },
          { rank: 'A' as Rank, action: 'Force draw', color: 'from-red-400 to-red-500' }
        ].map(({ rank, action, color }) => (
          <button
            key={rank}
            onClick={() => onDeclareAction(rank)}
            className={`bg-gradient-to-r ${color} hover:scale-105 text-white font-bold py-2 px-2 rounded-lg shadow transition-all text-xs`}
            title={`Declare ${rank}: ${action}`}
          >
            <div className="font-bold text-sm">{rank}</div>
            <div className="text-xs opacity-90">{action}</div>
          </button>
        ))}
      </div>

      {/* Non-action cards (shown but disabled/grayed) */}
      <div className="mt-2 text-center">
        <div className="text-xs text-yellow-600 mb-1">No action cards:</div>
        <div className="flex gap-1 justify-center">
          {['2', '3', '4', '5', '6', 'Joker'].map((rank) => (
            <button
              key={rank}
              disabled
              className="bg-gray-300 text-gray-500 font-bold py-1 px-2 rounded text-xs cursor-not-allowed"
              title={`${rank} has no action`}
            >
              {rank}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}