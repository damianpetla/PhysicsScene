import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

export default defineConfig({
  site: 'https://damianpetla.github.io',
  base: '/PhysicsScene',
  integrations: [
    starlight({
      title: 'PhysicsScene',
      description: 'Physics-first Jetpack Compose scene powered by Box2D.',
      social: [
        {
          icon: 'github',
          label: 'GitHub',
          href: 'https://github.com/damianpetla/PhysicsScene'
        }
      ],
      sidebar: [
        {
          label: 'Documentation',
          items: [
            { label: 'Introduction', slug: 'index' },
            { label: 'Getting Started', slug: 'getting-started' },
            { label: 'Core Concepts', slug: 'core-concepts' }
          ]
        }
      ],
      customCss: ['./src/styles/custom.css']
    })
  ]
});
