// Tipos de los pilares que todavía no publican su .d.ts (@dotrino/compat). Solo lo que usa esta
// app, copiado de su JSDoc. Cuando el pilar traiga sus tipos, esto se borra.

declare module '@dotrino/compat' {
  export interface Declaration {
    product: string
    version: string
    protocol: number
    speaks: number[]
    needs?: string[]
  }
  export function declare (d: { product: string, version: string, protocol: number, speaks?: number[], needs?: string[] }): Declaration
}
