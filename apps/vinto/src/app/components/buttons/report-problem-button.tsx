import { BugIcon } from 'lucide-react';

export const ReportProblemButton = ({ onClick }: { onClick: () => void }) => (
  <button
    onClick={onClick}
    className=" px-2 py-1 rounded 
        bg-surface-secondary hover:bg-surface-tertiary
        text-secondary hover:text-primary
        transition-colors
        flex items-center gap-1"
  >
    <BugIcon size={18} />
    <span className="text-xs font-medium hidden sm:inline">
      Report a problem
    </span>
  </button>
);
