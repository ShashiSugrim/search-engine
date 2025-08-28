module.exports = {
	apps: [
		{
			name: 'api',
			script: 'js_backend.js',
			instances: 1,
			exec_mode: 'fork',
			env: {
				RABBITMQ_URL: process.env.RABBITMQ_URL || 'amqp://localhost',
				RABBITMQ_QUEUE: process.env.RABBITMQ_QUEUE || 'search_queries',
			},
		},
		{
			name: 'worker',
			script: 'worker.js',
			instances: 4,
			exec_mode: 'cluster',
			env: {
				RABBITMQ_URL: process.env.RABBITMQ_URL || 'amqp://localhost',
				RABBITMQ_QUEUE: process.env.RABBITMQ_QUEUE || 'search_queries',
			},
		},
	],
};

