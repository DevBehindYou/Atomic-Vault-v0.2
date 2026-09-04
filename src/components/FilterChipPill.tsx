import React from 'react';

interface FilterChipPillProps {
  label: string;
  selected: boolean;
  onClick: () => void;
  count?: number;
  colorDot?: string | null;
  id?: string;
}

export const FilterChipPill: React.FC<FilterChipPillProps> = ({
  label,
  selected,
  onClick,
  count,
  colorDot,
  id,
}) => {
  return (
    <button
      id={id}
      onClick={onClick}
      type="button"
      className={`inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-full text-xs font-medium transition-all whitespace-nowrap cursor-pointer border ${
        selected
          ? 'bg-white text-black border-white shadow-sm font-semibold'
          : 'bg-white/5 hover:bg-white/10 text-neutral-300 border-white/15'
      }`}
    >
      {colorDot && (
        <span
          className="w-2 h-2 rounded-full inline-block"
          style={{ backgroundColor: colorDot }}
        />
      )}
      <span>{label}</span>
      {count !== undefined && (
        <span
          className={`text-[10px] px-1.5 py-0.2 rounded-full ${
            selected ? 'bg-black/15 text-black' : 'bg-white/10 text-neutral-400'
          }`}
        >
          {count}
        </span>
      )}
    </button>
  );
};
