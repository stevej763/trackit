import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import ScoreInput from './ScoreInput';

describe('ScoreInput', () => {
  it('reports the notch that was clicked', async () => {
    const onChange = vi.fn();
    render(<ScoreInput value={null} onChange={onChange} />);

    await userEvent.click(screen.getByRole('radio', { name: '7 out of 10' }));

    expect(onChange).toHaveBeenCalledWith(7);
  });

  it('clears the score when the lit notch is clicked again', async () => {
    const onChange = vi.fn();
    render(<ScoreInput value={7} onChange={onChange} />);

    await userEvent.click(screen.getByRole('radio', { name: '7 out of 10' }));

    expect(onChange).toHaveBeenCalledWith(null);
  });

  it('is reachable and adjustable from the keyboard', async () => {
    const onChange = vi.fn();
    render(<ScoreInput value={5} onChange={onChange} />);

    // A radio group takes one Tab stop, landing on the current value.
    await userEvent.tab();
    expect(screen.getByRole('radio', { name: '5 out of 10' })).toHaveFocus();

    await userEvent.keyboard('{ArrowRight}');
    expect(onChange).toHaveBeenLastCalledWith(6);

    await userEvent.keyboard('{End}');
    expect(onChange).toHaveBeenLastCalledWith(10);

    await userEvent.keyboard('{Backspace}');
    expect(onChange).toHaveBeenLastCalledWith(null);
  });

  it('stops at the ends of the scale', async () => {
    const onChange = vi.fn();
    render(<ScoreInput value={10} onChange={onChange} />);

    await userEvent.tab();
    await userEvent.keyboard('{ArrowRight}');

    expect(onChange).toHaveBeenLastCalledWith(10);
  });

  it('shows the score as a number, and a placeholder when there is none', () => {
    const { rerender } = render(<ScoreInput value={null} onChange={vi.fn()} />);
    expect(screen.getByText('--')).toBeInTheDocument();

    rerender(<ScoreInput value={8} onChange={vi.fn()} />);
    expect(screen.getByText('8')).toBeInTheDocument();
  });
});
