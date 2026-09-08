import React from 'react';

interface LiquidGlassCardProps {
  children: React.ReactNode;
  className?: string;
  onClick?: () => void;
  variant?: 'card' | 'subtle' | 'glow';
  id?: string;
}

export const LiquidGlassCard: React.FC<LiquidGlassCardProps> = ({
  children,
  className = '',
  onClick,
  variant = 'card',
  id,
}) => {
  const isClickable = !!onClick;

  return (
    <div
      id={id}
      onClick={onClick}
      className={`liquid-glass rounded-2xl p-4 sm:p-5 transition-all duration-200 ${
        isClickable ? 'cursor-pointer hover:bg-white/10 active:scale-[0.99]' : ''
      } ${
        variant === 'glow' ? 'border-white/40 shadow-[0_0_20px_rgba(255,255,255,0.08)]' : ''
      } ${className}`}
    >
      {children}
    </div>
  );
};
