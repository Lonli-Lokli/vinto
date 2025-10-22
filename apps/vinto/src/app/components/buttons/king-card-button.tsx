import { getButtonClasses } from '../../constants/button-colors';


const getKingCardButtonClasses = (actionable: boolean) => {
  return actionable
    ? `${getButtonClasses('king-action-card', false)} font-bold px-2`
    : `${getButtonClasses('king-non-action-card', false)} font-medium px-1.5`;
}

export const KingCardButton = ({
  rank,
  onClick,
  actionable,  
  className = '',
}: {
  rank: string;
  actionable: boolean;
  onClick: () => void;
  disabled?: boolean;
  className?: string;
}) => (
  <button
    onClick={onClick}
    className={`${getKingCardButtonClasses(  actionable)} py-1 text-xs flex items-center justify-center min-h-[32px] ${className}`}
    title={actionable ? `Execute ${rank} action` : `Declare ${rank} (no action)`}
  >
    {rank}
  </button>
);

