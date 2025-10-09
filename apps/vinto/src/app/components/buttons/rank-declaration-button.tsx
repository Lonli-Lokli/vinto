import { getButtonClasses } from '../../constants/button-colors';

export const RankDeclarationButton = ({
  rank,
  onClick,
  className = '',
}: {
  rank: string;
  onClick: () => void;
  className?: string;
}) => (
  <button
    onClick={onClick}
    className={`${getButtonClasses(
      'declare-rank'
    )} font-bold py-1.5 px-1 text-xs min-h-[36px] flex items-center justify-center ${className}`}
    title={`Declare ${rank}`}
  >
    {rank}
  </button>
);
