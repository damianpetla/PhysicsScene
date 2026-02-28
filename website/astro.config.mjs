import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

export default defineConfig({
  site: 'https://damianpetla.github.io',
  base: '/PhysicsScene',
  integrations: [
    starlight({
      title: 'PhysicsScene',
      description: 'Physics-first Jetpack Compose scene powered by Box2D.',
      social: {
        github: 'https://github.com/damianpetla/PhysicsScene'
      },
      editLink: {
        baseUrl: 'https://github.com/damianpetla/PhysicsScene/edit/main/website/'
      },
      head: [],
      sidebar: [
        {
          label: 'Start',
          items: [
            { label: 'Introduction', slug: 'index' },
            { label: 'Getting Started', slug: 'getting-started' }
          ]
        },
        {
          label: 'Low-Level API',
          items: [
            { label: 'PhysicsScene', slug: 'api/physics-scene' },
            { label: 'Modifier.physicsBody', slug: 'api/modifier-physics-body' },
            { label: 'PhysicsSceneState', slug: 'api/physics-scene-state' },
            { label: 'Events, Lifecycle, Snapshots', slug: 'api/events-lifecycle-snapshots' },
            { label: 'Body/Explosion Specs', slug: 'api/body-and-explosion-specs' }
          ]
        },
        {
          label: 'Effects',
          items: [
            { label: 'Overview', slug: 'effects/overview' },
            { label: 'FallingShatterEffect', slug: 'effects/falling-shatter-effect' },
            { label: 'CenterBurstEffect', slug: 'effects/center-burst-effect' },
            { label: 'CustomEffect', slug: 'effects/custom-effect' },
            { label: 'Effects Gallery (GIF-ready)', slug: 'effects/gallery' }
          ]
        },
        {
          label: 'Examples',
          items: [
            { label: 'Falling Shatter Demo', slug: 'examples/falling-shatter-demo' },
            { label: 'Center Burst Demo', slug: 'examples/center-burst-demo' },
            { label: 'Shard Recall Demo', slug: 'examples/shard-recall-demo' },
            { label: 'Emoji Cannon Demo', slug: 'examples/emoji-cannon-demo' }
          ]
        }
      ],
      customCss: ['./src/styles/custom.css']
    })
  ]
});
