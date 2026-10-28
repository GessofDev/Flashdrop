import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { Badge, Button, Card } from '@/components/ui';

import { listSourceFiles } from './support/sourceFiles';

const HEX_LITERAL = /#[0-9a-fA-F]{3,8}\b/;

describe('Button primitive', () => {
  it('renders the primary variant from the design tokens', () => {
    render(<Button>Confirmar pedido</Button>);

    const button = screen.getByRole('button', { name: 'Confirmar pedido' });

    expect(button).toHaveClass('bg-primary');
    expect(button).toHaveClass('hover:bg-primary-hover');
    expect(button).toHaveClass('rounded-control');
    expect(button).not.toHaveClass('bg-destructive');
  });

  it('exposes the accessible focus ring', () => {
    render(<Button>Confirmar pedido</Button>);

    const button = screen.getByRole('button', { name: 'Confirmar pedido' });

    expect(button).toHaveClass('focus-visible:outline-2');
    expect(button).toHaveClass('focus-visible:outline-primary');
    expect(button).toHaveClass('focus-visible:outline-offset-2');
  });

  it('never paints the button with an inline color', () => {
    render(<Button>Confirmar pedido</Button>);

    const button = screen.getByRole('button', { name: 'Confirmar pedido' });

    expect(button.getAttribute('style')).toBeNull();
  });

  it('renders the secondary variant from the design tokens', () => {
    render(<Button variant="secondary">Volver</Button>);

    const button = screen.getByRole('button', { name: 'Volver' });

    expect(button).toHaveClass('bg-canvas');
    expect(button).toHaveClass('border-border');
    expect(button).not.toHaveClass('bg-primary');
  });

  it('renders the destructive variant from the design tokens', () => {
    render(<Button variant="destructive">Cancelar</Button>);

    const button = screen.getByRole('button', { name: 'Cancelar' });

    expect(button).toHaveClass('bg-destructive');
    expect(button).toHaveClass('hover:bg-destructive-hover');
    expect(button).not.toHaveClass('bg-primary');
  });
});

describe('Badge primitive', () => {
  it('renders the success tone from the design tokens', () => {
    render(<Badge variant="success">Entregado</Badge>);

    const badge = screen.getByText('Entregado');

    expect(badge).toHaveClass('bg-success-surface');
    expect(badge).toHaveClass('text-success');
  });

  it('renders the info tone from the design tokens', () => {
    render(<Badge variant="info">Verificado</Badge>);

    const badge = screen.getByText('Verificado');

    expect(badge).toHaveClass('bg-info-surface');
    expect(badge).toHaveClass('text-info');
  });

  it('renders the warning tone from the design tokens', () => {
    render(<Badge variant="warning">Pendiente</Badge>);

    const badge = screen.getByText('Pendiente');

    expect(badge).toHaveClass('bg-warning-surface');
    expect(badge).toHaveClass('text-warning');
  });

  it('renders the destructive tone from the design tokens', () => {
    render(<Badge variant="destructive">Rechazado</Badge>);

    const badge = screen.getByText('Rechazado');

    expect(badge).toHaveClass('bg-destructive-surface');
    expect(badge).toHaveClass('text-destructive');
  });
});

describe('Card primitive', () => {
  it('renders the container radius, surface and border from the design tokens', () => {
    render(<Card>Contenido</Card>);

    const card = screen.getByText('Contenido').closest('div');

    expect(card).not.toBeNull();
    expect(card).toHaveClass('bg-surface');
    expect(card).toHaveClass('rounded-container');
    expect(card).toHaveClass('border-border');
  });
});

describe('UI primitives', () => {
  it('never hardcodes a hex literal', () => {
    const files = listSourceFiles('components');

    expect(files.length).toBeGreaterThan(0);

    for (const file of files) {
      expect(file.content, `${file.path} must not hardcode a hex literal`).not.toMatch(HEX_LITERAL);
    }
  });
});
