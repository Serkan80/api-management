function spa() {
	return {
        currentRoute: '/',
        currentPage: '',
        routes: {
            '/': 'index.html',
            '/login': '../login.html',
            '/my-subscription': 'my-subscription.html',
            '/subscription': 'subscription.html',
            '/subscriptions': 'subscriptions.html',
            '/apis': 'apis.html',
            '/analytics': 'analytics.html'
        },
        username: null,
        roles: [],

        // Initialize the SPA
        init() {
            const loggedIn = this.isLoggedIn();
            if (!loggedIn) {
                this.determineAuthentication();
            } else {
                this.username = sessionStorage.getItem('username');
                this.roles = sessionStorage.getItem('roles').split(',');
                window.addEventListener('hashchange', () => this.loadRoute());
            }
        },

        // Load the route based on the hash
        loadRoute() {
            console.log("loading route, hash changed: " + window.location.hash);
            var hashUrl = window.location.hash.slice(1);
            var start = hashUrl.indexOf('?');
            if (start > -1) {
                hashUrl = hashUrl.slice(0, start);
            }

            this.currentRoute = hashUrl || '/';
            this.loadPage(this.currentRoute);
        },

        // Dynamically load page content
        async loadPage(route) {
            window.dispatchEvent(new CustomEvent('page-changed'));
            const index = route.indexOf('_') > -1 ? route.indexOf('_') : route.length;
            const path = route.substring(0, index);
            const page = this.routes[route];

            if (page) {
                this.currentPage = fetchPage(page);
            } else {
                this.currentPage = fetchPage('../404.html');
            }
        },

        determineAuthentication() {
            const options = { headers: {'Content-Type': 'application/json'}};
            fetch("http://localhost:8080/apim/core/apis", options)
                .then(res => {
                    if (res.status >= 400) {
                        window.location.href = '../login.html';
                    }
                    // else a redirect occurs to the OIDC server
                })
                .catch(err => {
                    window.location.href = '../login.html';
                });
        },

        isLoggedIn() {
            return sessionStorage.getItem('username') !== null;
        },

        logout() {
            sessionStorage.clear();
            this.init();
        }
    };
}

async function fetchPage(page) {
    let res = await fetch(page);
    if (!res.ok) {
        res = await fetch('../404.html');
        return await res.text();
    }
    return await res.text();
}

function fetchData() {
    return {
	    isLoading: false,
		data: [],
		postData: {},
		selectedData: {},
		selectedRows: [],
		showForm: false,
		isInsert: true,
		isModalInsert: true,
		errors: null,
		baseUrl: 'http://localhost:8080/apim/core',

		get(url) {
		    this.isLoading = true;
		    const options = { headers: {'Content-Type': 'application/json'}, credentials: 'include' };

			fetchInterceptor(`${this.baseUrl}${url}`, options)
				.then(res => res.json())
				.then(json => {
					this.isLoading = false;
					this.data = json;
				});
		},

		search(url, searchVal) {
            this.isLoading = true;
            const options = { headers: {'Content-Type': 'application/json'}, credentials: 'include' };

            fetchInterceptor(`${this.baseUrl}${url}${searchVal}`, options)
                .then(res => res.json())
                .then(json => {
                    this.isLoading = false;
                    this.data = json;
                });
        },

		post(formId, url, body, toPage) {
		    let form = document.querySelector(formId);
	        form.classList.add('was-validated');

            if (form.checkValidity()) {
                const method = this.isInsert ? 'post' : 'put';
                const options = { headers: {'Content-Type': 'application/json'}, credentials: 'include', method: method, body: JSON.stringify(body) };

                fetchInterceptor(`${this.baseUrl}${url}`, options)
                    .then(res => {
                        if (!res.ok) {
                            return res.json().then(err => {
                                if (err.violations) {
                                    this.errors = new Array();
                                    err.violations.forEach(ex => this.errors.push(`<li>${ex.field.split('.').at(-1)}: ${ex.message}</li>`));
                                    this.errors = "<ul style='margin:0'>" + this.errors.join([separator='\n']) + "</ul>";
                                } else if (err.message) {
                                    this.errors = err.message;
                                } else {
                                    this.errors = Object.values(err).join([separator='\n']);
                                }
                                throw new Error("response contains error");
                            });
                        }
                    })
                   .then(data => {
                        this.showForm = false;
                        this.postData = {};
                        if (toPage) {
                            this.loadPage(toPage);
                        }
                   })
                   .catch(err => console.log(err));
            }
		},

		edit(elem, insert) {
			this.errors = null;
			this.isInsert = insert;
			this.showForm = true;
			if (elem && elem !== '') {
				this.postData = elem;
			}
		},

		findBy(url) {
			const options = { headers: {'Content-Type': 'application/json'}, credentials: 'include' };
            fetchInterceptor(`${this.baseUrl}${url}`, options)
                .then(res => res.json())
                .then(json => {
                    this.errors = null;
                    this.isInsert = false;
                    this.showForm = true;
                    this.selectedData = json;
                    if (this.selectedData.apis) {
                        this.selectedData.apis.sort((a, b) => b.id - a.id);
                    }
                });
		},

		toggleRow(id) {
			if (this.selectedRows.indexOf(id) > -1) {
				this.selectedRows = this.selectedRows.filter(i => i !== id);
			} else {
				this.selectedRows.push(id);
			}
		},

		addApis() {
			if (this.selectedRows.length > 0) {
				const options = { headers: {'Content-Type': 'application/json'}, credentials: 'include', method: 'post', body: JSON.stringify(this.selectedRows) };

	            fetchInterceptor(`${this.baseUrl}/subscriptions/${this.selectedData.subscriptionKey}/apis`, options)
	               .then(res => res.json())
	               .then(data => {
	                    this.selectedRows = [];
	                    this.selectedData.apis = data.apis.sort((a, b) => b.id - a.id);
	               })
	               .catch(err => console.log(err));
           }
           document.querySelector('#modalClose').dispatchEvent(new MouseEvent('click', {
                "view": window,
                "bubbles": true,
                "cancelable": false
           }));
		},

		addCredentials(formId, url, body) {
			if (this.selectedData.apis && this.selectedData.apis.length > 0) {
				body.subscriptionKey = document.querySelector('#credentialsKey').value;

				let form = document.querySelector(formId);
                form.classList.add('was-validated');

                if (form.checkValidity()) {
                    const options = { headers: {'Content-Type': 'application/json'}, credentials: 'include', method: this.isModalInsert ? 'post' : 'put', body: JSON.stringify(body) };

                    fetchInterceptor(`${this.baseUrl}${url}`, options)
                        .then(res => {
                            if (!res.ok) {
                                return res.json().then(err => {
                                     if (err.violations) {
                                         this.errors = new Array();
                                         err.violations.forEach(ex => this.errors.push(`<li>${ex.field.split('.').at(-1)}: ${ex.message}</li>`));
                                         this.errors = "<ul style='margin:0'>" + this.errors.join([separator='\n']) + "</ul>";
                                     } else if (err.message) {
                                         this.errors = err.message;
                                     } else {
                                         this.errors = Object.values(err).join([separator='\n']);
                                     }
                                     throw new Error("response contains error");
                                });
                            }
                        })
                       .then(data => {
                            this.errors = null;
                            this.postData = {};
                            document.querySelector('#modalCloseCredentials').dispatchEvent(new MouseEvent('click', {
                                "view": window,
                                "bubbles": true,
                                "cancelable": false
                           }));
                       })
                       .catch(err => console.log(err));
				}
			} else {
				alert(`Api with ID ${body.apiId} is not present in this subscription. \n\nPlease add the API to this subscription first.`);
			}
		},

		getSelectedCredential(apiId) {
            return this.selectedData.credentials.filter(cr => cr.apiId == apiId);
        }
	}
}

function sse() {
	return {
	    source: null,
		metrics: null,
		baseUrl: 'http://localhost:8080/apim/prometheus/metrics',

		stream(threshold) {
		    const promQueries = [
		        '?query=sum by (proxyPath, status) (apim_metrics_seconds_count)',
		        'query=sum by (proxyPath, subscription) (apim_metrics_seconds_count)',
		        'query=avg by (proxyPath) (apim_metrics_seconds_max)',
		        'query=sum by (proxyPath) (apim_metrics_seconds_count)',
		        'query=sum(apim_metrics_seconds_max*apim_metrics_seconds_count)/sum(apim_metrics_seconds_count)',
		        'query=sum(apim_metrics_seconds_count)'
		    ];
		    const queries = promQueries.join('&');

			this.metrics = [];
			this.source = new EventSource(this.baseUrl + queries, { withCredentials: true });
		    this.source.onmessage = (event) => {
		        const data = JSON.parse(event.data).data;

		        if (data.result.length == 1) {
			        const value = data.result[0].value[1];
			        if (value.indexOf('.') > -1) {
			            this.metrics[1] = parseFloat(value).toFixed(4);
			        } else {
			            this.metrics[0] = value;
			        }
		        } else if (data.result.length > 1) {
		            const isAvgSet = data.result.every(metric => metric.value[1].indexOf('.') > -1 || metric.value[1] === '0');
					const isTotalPerSub = data.result[0].metric.hasOwnProperty('subscription');
					const isTotalPerStatus = data.result[0].metric.hasOwnProperty('status');

		            if (isAvgSet) {
		                this.metrics[3] = getAvgResponseTimes(data);
		            } else if (isTotalPerSub) {
		                this.metrics[4] = getTotalsPerSub(data, threshold);
		            } else if (isTotalPerStatus) {
		                this.metrics[5] = getTotalPerStatus(data, threshold);
		            } else {
		                this.metrics[2] = getTotalCounts(data, threshold);
		            }
		        }
		    };

		    this.source.onerror = (event) => {
		        console.log('Error occured: ' + event);
		        this.closeSse();
		    };
	    },

        // need to do it like this, otherwise quotes in query param causes CORS errors
	    streamForMySub(threshold, subName) {
	        this.closeSse();
	        const promQueries = [
                `sum by (proxyPath) (apim_metrics_seconds_count{subscription="${subName}"})`,
                `sum by (httpPath, subscription) (apim_metrics_seconds_count{subscription="${subName}"})`
            ];
	        let queryParam = new URLSearchParams({query: promQueries[0]});
	        queryParam.append("query", promQueries[1]);

			this.metrics = [];
            this.source = new EventSource(`${this.baseUrl}?${queryParam.toString()}`, { withCredentials: true });
            this.source.onmessage = (event) => {
                const data = JSON.parse(event.data).data;
                const isTotalPerSub = data.result[0].metric.hasOwnProperty('proxyPath');

                if (isTotalPerSub) {
                    this.metrics[4] = getTotalsPerSub(data, threshold);
                } else {
                    this.metrics[6] = getTotalRequestsPerSub(data);
                }
            };

            this.source.onerror = (event) => {
                console.log('Error occured: ' + event);
                this.closeSse();
            };
        },

	    closeSse() {
	        if (this.source) {
		        this.source.close();
		        this.source = null;
		        this.metrics = null;
		        console.log('sse closed');
	        }
	    }
    }
}

function metricTemplate(data, filter, mapper) {
    var result = data.result.filter(filter).map(mapper);
    return result.sort((a, b) => b.value - a.value).slice(0, 10);
}

function getTotalCounts(data, threshold) {
    return metricTemplate(
                    data,
                    row => parseInt(row.value[1]) > threshold,
                    row => { return {proxyPath: row.metric.proxyPath, value: row.value[1]}; });
}

function getAvgResponseTimes(data) {
	return metricTemplate(
                        data,
                        row => row.value[1] != '0',
                        row => { return {proxyPath: row.metric.proxyPath, value: parseFloat(row.value[1]).toFixed(4)}; });
}

function getTotalsPerSub(data, threshold) {
	return metricTemplate(
                        data,
                        row => parseInt(row.value[1]) > threshold,
                        row => { return {proxyPath: row.metric.proxyPath, sub: row.metric.subscription, value: row.value[1]}; });
}

function getTotalPerStatus(data, threshold) {
	return metricTemplate(
                        data,
                        row => parseInt(row.value[1]) > threshold,
                        row => { return {proxyPath: row.metric.proxyPath, status: row.metric.status, value: row.value[1]}; });
}

function getTotalRequestsPerSub(data) {
	return metricTemplate(
                        data,
                        row => true,
                        row => { return {httpPath: row.metric.httpPath, value: row.value[1]}; });
}

function authBasic() {
	return {
		username: null,
		password: null,

		login() {
			const options = {
				headers: {'Content-Type': 'application/json', 'Authorization': 'Basic ' + btoa(`${this.username}:${this.password}`)},
				method: 'post',
				credentials: 'include'
			};

            fetch('http://localhost:8080/apim/auth/token/web', options)
                .then(res => {
                    if (res.ok)
                      return res.json();
                    else
                      throw new Error("Authentication failed: " + res);
                })
                .then(data => {
                    sessionStorage.setItem("username", data.username);
                    sessionStorage.setItem("roles", data.roles);
                    window.location.href = "pages/index.html";
                })
                .catch((err) => console.log(err));
		}
	}
}


async function fetchInterceptor(url, options = {}) {
    // First attempt
    const response = await fetch(url, options);

    if (response.status !== 401) {
        return response;
    }

    await refreshAccessToken();
	console.log("Access Token refreshed");

    // Retry the original request with new token
    return fetch(url, options);
}

async function refreshAccessToken() {
    const response = await fetch('http://localhost:8080/apim/auth/refresh', {
        method: 'post',
        credentials: 'include'
    });

    if (!response.ok) {
        sessionStorage.clear();
        console.log('Failed to refresh token');
    }
}
